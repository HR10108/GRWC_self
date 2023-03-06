package no.nordicsemi.android.nrftoolbox.compass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.graphics.Rect;
import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.utility.DataConvey;

public class CompassView extends View implements SensorEventListener {
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private Paint paint;
    private float direction = 0;
    private SensorManager sensorManager;

    public CompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(Color.rgb(0,108,57));

        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
    }

    private float TransferData1(float Data1){
        if (Data1 > 0){
            return Data1 %360;
        }else{
            return (360+Data1)%360;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(Color.rgb(0,108,57));
//        direction = TransferData1(DataConvey.yaw);
        Log.e("hello_compass_onDraw", String.valueOf(direction));
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int radius = Math.min(cx, cy) - 20;

        canvas.drawCircle(cx, cy, radius, paint);

        canvas.save();
        canvas.rotate(direction, cx, cy);

        paint.setColor(Color.RED);
        Path path = new Path();
        path.moveTo(cx, cy - radius + 60);
        path.lineTo(cx - 10, cy - radius + 100);
        path.lineTo(cx, cy - radius + 70);
        path.lineTo(cx + 10, cy - radius + 100);
        path.close();
        canvas.drawPath(path, paint);
        canvas.restore();

        paint.setColor(Color.rgb(0,108,57));
        paint.setTextSize(30);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        Rect rect = new Rect();
        for (int i = 0; i < DIRECTIONS.length; i++) {
            String direction = DIRECTIONS[i];
            paint.getTextBounds(direction, 0, direction.length(), rect);
            float textWidth = paint.measureText(direction);
            float angle = (float) Math.toRadians(i * (360f / DIRECTIONS.length));
            float x = cx + (radius - 50) * (float) Math.sin(angle) - textWidth / 2;
            float y = cy - (radius - 50) * (float) Math.cos(angle) + rect.height() / 2;
            canvas.drawText(direction, x, y, paint);
        }

        paint.setTypeface(Typeface.DEFAULT);
        // 计算方向和度数
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};
        int angle = (int) direction;
        int index = (angle) / 45;
        String degree = String.valueOf(angle) + "°";
        String direction = directions[index];

        paint.setTextSize(80);
        String text = degree + " " + direction;
        float textWidth = paint.measureText(text);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;

        // 计算文字起始位置
        float x = cx - textWidth / 2;
        float y = cy + textHeight / 2;
        // 绘制文字
        canvas.drawText(text, x, y-25, paint);

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            direction = event.values[0];
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void start() {
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

}
