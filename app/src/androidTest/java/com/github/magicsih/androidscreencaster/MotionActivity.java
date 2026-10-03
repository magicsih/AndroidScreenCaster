package com.github.magicsih.androidscreencaster;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.WindowManager;

/** A deterministic moving screen for decoding verification, included only in the test APK. */
public final class MotionActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final long start = android.os.SystemClock.elapsedRealtime();
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Color.rgb(245, 247, 250));
                long elapsed = android.os.SystemClock.elapsedRealtime() - start;
                float position = (elapsed % 4000) / 4000f;
                paint.setColor(Color.rgb(20, 100, 210));
                canvas.drawCircle(60 + (getWidth() - 120) * position, getHeight() / 2f, 60, paint);
                paint.setColor(Color.rgb(220, 80, 20));
                canvas.drawRect(getWidth() * (1 - position) - 60, getHeight() / 3f,
                        getWidth() * (1 - position) + 60, getHeight() / 3f + 100, paint);
                paint.setColor(Color.DKGRAY); paint.setTextSize(38);
                canvas.drawText("ScreenCaster decode test", 30, 100, paint);
                canvas.drawText("Elapsed: " + elapsed + " ms", 30, 160, paint);
                postInvalidateDelayed(33);
            }
        });
    }
}
