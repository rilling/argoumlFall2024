package org.argouml.uml.diagram.ui;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import org.argouml.model.Model;
import org.argouml.ui.UndoableAction;

/**
 * Action to set the Multiplicity.
 */
public class ActionMultiplicity extends UndoableAction {
    private String str = "";
    private Object mult = null;

    // Multiplicity actions
    private static UndoableAction srcMultOne = new ActionMultiplicity("1", "src");
    private static UndoableAction destMultOne = new ActionMultiplicity("1", "dest");
    private static UndoableAction srcMultZeroToOne = new ActionMultiplicity("0..1", "src");
    private static UndoableAction destMultZeroToOne = new ActionMultiplicity("0..1", "dest");
    private static UndoableAction srcMultZeroToMany = new ActionMultiplicity("0..*", "src");
    private static UndoableAction destMultZeroToMany = new ActionMultiplicity("0..*", "dest");
    private static UndoableAction srcMultOneToMany = new ActionMultiplicity("1..*", "src");
    private static UndoableAction destMultOneToMany = new ActionMultiplicity("1..*", "dest");

    /**
     * Constructor.
     * @param m the multiplicity
     * @param s "src" or "dest". Anything else is interpreted as "dest".
     */
    protected ActionMultiplicity(String m, String s) {
        super(m, null);
        putValue(Action.SHORT_DESCRIPTION, m);
        str = s;
        mult = m;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        super.actionPerformed(ae);
        Object ascEnd = ActionHelper.getSelectedAssociationEnd(str);
        if (ascEnd != null && !mult.equals(Model.getFacade().toString(Model.getFacade().getMultiplicity(ascEnd)))) {
            Model.getCoreHelper().setMultiplicity(ascEnd, (String) mult);
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // Getters for multiplicity actions
    public static UndoableAction getSrcMultOne() { return srcMultOne; }
    public static UndoableAction getDestMultOne() { return destMultOne; }
    public static UndoableAction getSrcMultZeroToOne() { return srcMultZeroToOne; }
    public static UndoableAction getDestMultZeroToOne() { return destMultZeroToOne; }
    public static UndoableAction getSrcMultZeroToMany() { return srcMultZeroToMany; }
    public static UndoableAction getDestMultZeroToMany() { return destMultZeroToMany; }
    public static UndoableAction getSrcMultOneToMany() { return srcMultOneToMany; }
    public static UndoableAction getDestMultOneToMany() { return destMultOneToMany; }
}
