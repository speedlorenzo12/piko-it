package app.crimera.patches.twitter.misc.customize.font

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

private val customFontHookFingerprint =
    fingerprint {
        strings("end should be < than charSequence length")
        custom { _, classDef ->
            classDef.type.contains("emoji2/text")
        }
    }

@Suppress("unused")
val customFontHook =
    bytecodePatch(
        description = "Hook to customise font",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            customFontHookFingerprint.method.apply {

                val charSeqReg = (instructions.first { it.opcode == Opcode.INVOKE_INTERFACE } as Instruction35c).registerC
                addInstructions(
                    0,
                    """
                    invoke-static {v$charSeqReg}, ${Constants.PATCHES_DESCRIPTOR}/customise/font/UpdateFont;->process(Ljava/lang/CharSequence;)Landroid/text/Spannable;
                    move-result-object v$charSeqReg
                    """.trimIndent(),
                )
            }
        }
    }
