package org.argouml.uml.diagram.ui;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import org.argouml.i18n.Translator;
import org.argouml.model.Model;
import org.argouml.ui.UndoableAction;

/**
 * Action to set the Aggregation kind.
 */
public class ActionAggregation extends UndoableAction {

    public static final int NONE = 0;
    public static final int AGGREGATE_END1 = 1;
    public static final int AGGREGATE_END2 = 2;
    public static final int COMPOSITE_END1 = 3;
    public static final int COMPOSITE_END2 = 4;

    private String str = "";
    private Object agg = null;
    Object associationEnd1;
    Object associationEnd2;
    int aggr;

    // Aggregation actions
    private static UndoableAction srcAgg = new ActionAggregation(Model.getAggregationKind().getAggregate(), "src");
    private static UndoableAction destAgg = new ActionAggregation(Model.getAggregationKind().getAggregate(), "dest");
    private static UndoableAction srcAggComposite = new ActionAggregation(Model.getAggregationKind().getComposite(), "src");
    private static UndoableAction destAggComposite = new ActionAggregation(Model.getAggregationKind().getComposite(), "dest");
    private static UndoableAction srcAggNone = new ActionAggregation(Model.getAggregationKind().getNone(), "src");
    private static UndoableAction destAggNone = new ActionAggregation(Model.getAggregationKind().getNone(), "dest");

    /**
     * Constructor with aggregation type and label.
     */
    private ActionAggregation(final String label, final Object associationEnd1, final Object associationEnd2, final int aggr) {
        super(label, null);
        putValue(Action.SHORT_DESCRIPTION, label);
        this.aggr = aggr;
        this.associationEnd1 = associationEnd1;
        this.associationEnd2 = associationEnd2;
    }

    protected ActionAggregation(Object a, String s) {
        super(Translator.localize(Model.getFacade().getName(a)), null);
        putValue(Action.SHORT_DESCRIPTION, Translator.localize(Model.getFacade().getName(a)));
        str = s;
        agg = a;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (agg != null) {
            oldActionPerformed(ae);
        } else {
            super.actionPerformed(ae);
            if (aggr == AGGREGATE_END1) {
                Model.getCoreHelper().setAggregation2(associationEnd1, Model.getAggregationKind().getAggregate());
                Model.getCoreHelper().setAggregation2(associationEnd2, Model.getAggregationKind().getNone());
            } else if (aggr == AGGREGATE_END2) {
                Model.getCoreHelper().setAggregation2(associationEnd1, Model.getAggregationKind().getNone());
                Model.getCoreHelper().setAggregation2(associationEnd2, Model.getAggregationKind().getAggregate());
            } else if (aggr == COMPOSITE_END1) {
                Model.getCoreHelper().setAggregation2(associationEnd1, Model.getAggregationKind().getComposite());
                Model.getCoreHelper().setAggregation2(associationEnd2, Model.getAggregationKind().getNone());
            } else if (aggr == COMPOSITE_END2) {
                Model.getCoreHelper().setAggregation2(associationEnd1, Model.getAggregationKind().getNone());
                Model.getCoreHelper().setAggregation2(associationEnd2, Model.getAggregationKind().getComposite());
            } else {
                Model.getCoreHelper().setAggregation2(associationEnd1, Model.getAggregationKind().getNone());
                Model.getCoreHelper().setAggregation2(associationEnd2, Model.getAggregationKind().getNone());
            }
        }
    }

    public void oldActionPerformed(ActionEvent ae) {
        super.actionPerformed(ae);
        Object ascEnd = ActionHelper.getSelectedAssociationEnd(str);
        if (ascEnd != null) {
            Model.getCoreHelper().setAggregation(ascEnd, agg);
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // Getters for aggregation actions
    public static UndoableAction getSrcAgg() { return srcAgg; }
    public static UndoableAction getDestAgg() { return destAgg; }
    public static UndoableAction getSrcAggComposite() { return srcAggComposite; }
    public static UndoableAction getDestAggComposite() { return destAggComposite; }
    public static UndoableAction getSrcAggNone() { return srcAggNone; }
    public static UndoableAction getDestAggNone() { return destAggNone; }
}
