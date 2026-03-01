package com.example.arcibald160.sopilatranscriptor.helpers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class VisualizerView extends View {
    private static final int MAX_BARS = 80; // Slightly fewer bars for better clarity
    private static final int BAR_GAP_PX = 3; // Slightly wider gap for a cleaner look
    private List<Float> amplitudes = new ArrayList<>();
    private Paint paint = new Paint();

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        // Pre-fill with zeros so it always spans the full width of the view
        for (int i = 0; i < MAX_BARS; i++) {
            amplitudes.add(0f);
        }
    }

    public void addAmplitude(float amplitude) {
        amplitudes.add(amplitude);
        if (amplitudes.size() > MAX_BARS) {
            amplitudes.remove(0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float barWidth = width / MAX_BARS;

        for (int i = 0; i < amplitudes.size(); i++) {
            float amplitude = amplitudes.get(i);
            
            // SIGNIFICANTLY boost the amplitude visually to ensure it uses the full vertical space
            // 2.5x multiplier to make the waveform "pop" and reach the edges more easily
            float scaledAmplitude = Math.min(1.0f, amplitude * 2.5f);
            
            float barHeight = scaledAmplitude * height; 
            if (barHeight < 6) barHeight = 6; // slightly taller minimum for visibility
            
            float left = i * barWidth;
            // Center the bars vertically
            float top = (height - barHeight) / 2;
            
            // Add a gap by subtracting from the right edge
            float right = (i + 1) * barWidth - BAR_GAP_PX; 
            float bottom = top + barHeight;
            
            // Ensure right is at least greater than left
            if (right <= left) right = left + 1;

            canvas.drawRect(left, top, right, bottom, paint);
        }
    }

    public void clear() {
        amplitudes.clear();
        for (int i = 0; i < MAX_BARS; i++) {
            amplitudes.add(0f);
        }
        invalidate();
    }
}
