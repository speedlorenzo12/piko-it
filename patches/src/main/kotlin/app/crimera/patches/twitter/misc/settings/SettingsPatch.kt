package app.crimera.patches.twitter.misc.settings

import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.utils.Constants.ACTIVITY_HOOK_CLASS
import app.crimera.utils.Constants.ADD_PREF_DESCRIPTOR
import app.crimera.utils.Constants.DEEPLINK_HOOK_CLASS
import app.crimera.utils.Constants.SSTS_DESCRIPTOR
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.shared.misc.extension.integrationsUtilsFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val START_ACTIVITY_DESCRIPTOR =
    "invoke-static {}, $ACTIVITY_HOOK_CLASS->startSettingsActivity()V"

val settingsPatch =
    bytecodePatch(
        name = "Adds settings",
    ) {
        compatibleWith("com.twitter.android")
        extendWith("extensions/twitter.rve")

        dependsOn(
            sharedExtensionPatch,
            settingsResourcePatch,
        )

        execute {
            val methods = settingsFingerprint.classDef.methods
            val initMethod = methods.first()
            val arrayCreation =
                initMethod
                    .instructions
                    .first { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }
                    .location.index + 1

            initMethod.getInstruction<BuilderInstruction11x>(arrayCreation).registerA.also { reg ->
                initMethod.addInstructions(
                    arrayCreation + 1,
                    """
                const-string v1, "pref_mod"
                invoke-static {v$reg, v1}, $ADD_PREF_DESCRIPTOR
                move-result-object v$reg
            """,
                )
            }

            val prefCLickedMethod = methods.find { it.returnType == "Z" }!!
            val constIndex =
                prefCLickedMethod
                    .instructions
                    .first { it.opcode == Opcode.CONST_4 }
                    .location.index

            val igetObjLoc =
                prefCLickedMethod
                    .instructions
                    .first { it.opcode == Opcode.IGET_OBJECT }
                    .location.index
            val objFieldName = (prefCLickedMethod.getInstruction<ReferenceInstruction>(igetObjLoc).reference as FieldReference).name
            prefCLickedMethod.removeInstruction(igetObjLoc)

            prefCLickedMethod.addInstructionsWithLabels(
                0,
                """
            iget-object p1, p1, Landroidx/preference/Preference;->$objFieldName:Ljava/lang/String;
            const-string v1, "pref_mod" 
            invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v2

            if-nez v2, :start
            goto :cont
            
            :start
            $START_ACTIVITY_DESCRIPTOR
            const/4 v3, 0x1
            return v3 
        """,
                ExternalLabel("cont", prefCLickedMethod.getInstruction(constIndex)),
            )

            val authAppMethod = authorizeAppActivity.method
            authAppMethod.addInstructionsWithLabels(
                1,
                """
                invoke-static {p0}, $ACTIVITY_HOOK_CLASS->create(Landroid/app/Activity;)Z
                move-result v0
                if-nez v0, :no_piko_settings_init
                """.trimIndent(),
                ExternalLabel(
                    "no_piko_settings_init",
                    authAppMethod.instructions.first { it.opcode == Opcode.RETURN_VOID },
                ),
            )

            val urlInterActMethod = urlInterpreterActivity.method
            val instructions = urlInterActMethod.instructions
            val loc = instructions.first { it.opcode == Opcode.INVOKE_SUPER }.location.index + 1
            urlInterActMethod.addInstructionsWithLabels(
                loc,
                """
                invoke-static {p0}, $DEEPLINK_HOOK_CLASS->deeplink(Landroid/app/Activity;)Z
                move-result v0
                if-nez v0, :deep_link
                """.trimIndent(),
                ExternalLabel(
                    "deep_link",
                    instructions.first { it.opcode == Opcode.RETURN_VOID },
                ),
            )

            integrationsUtilsFingerprint.method.addInstruction(
                0,
                "$SSTS_DESCRIPTOR->load()V",
            )

            // execute ends.
        }
    }
