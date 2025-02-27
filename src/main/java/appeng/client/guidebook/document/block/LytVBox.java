package appeng.client.guidebook.document.block;

import appeng.client.guidebook.document.LytRect;
import appeng.client.guidebook.layout.LayoutContext;
import appeng.client.guidebook.layout.Layouts;

/**
 * Lays out its children vertically.
 */
public class LytVBox extends LytBox {
    private int gap = 0;

    public int getGap() {
        return gap;
    }

    public void setGap(int gap) {
        this.gap = gap;
    }

    @Override
    protected LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth) {
        // Padding is applied through the parent
        return Layouts.verticalLayout(context,
                children,
                x,
                y,
                availableWidth,
                0,
                0,
                0,
                0,
                gap);
    }
}
