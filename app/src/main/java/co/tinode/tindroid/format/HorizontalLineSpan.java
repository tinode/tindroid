package co.tinode.tindroid.format;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

public class HorizontalLineSpan extends ReplacementSpan {
    private final int color;
    private final float thickness;

    public HorizontalLineSpan(int color, float thickness) {
        this.color = color;
        this.thickness = thickness;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        return 0; // The line will occupy the width of the container
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        paint.setColor(color);
        paint.setStrokeWidth(thickness);

        // Draw line in the middle of the text line height
        float middleY = (top + bottom) / 2f;
        canvas.drawLine(0, middleY, canvas.getWidth(), middleY, paint);
    }
}
