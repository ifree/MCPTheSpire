package mcpthespire.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.events.GenericEventDialog;
import mcpthespire.GameStateListener;

@SpirePatch(
        clz= GenericEventDialog.class,
        method="setDialogOption",
        paramtypez = {String.class}
)
public class SetDialogOptionPatch {
    public static void Postfix(GenericEventDialog _instance, String _arg) {
        GameStateListener.registerStateChange();
    }
}
