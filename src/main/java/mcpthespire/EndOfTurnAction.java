package mcpthespire;

import com.megacrit.cardcrawl.actions.AbstractGameAction;

/**
 * Action that signals the end of the player's turn to the GameStateListener.
 */
public class EndOfTurnAction extends AbstractGameAction {

    public EndOfTurnAction() {
        this.actionType = ActionType.SPECIAL;
    }

    @Override
    public void update() {
        GameStateListener.signalTurnEnd();
        this.isDone = true;
    }
}
