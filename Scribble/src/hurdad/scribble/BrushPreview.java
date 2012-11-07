package hurdad.scribble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class BrushPreview extends View {
	Paint paint;
	float centerX;
	float centerY;
	private final float STROKE_WIDTH = 8.0f;

	public BrushPreview(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint = new Paint();
		paint.setColor(Color.BLACK);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(STROKE_WIDTH);
		paint.setAntiAlias(true);
		paint.setStrokeJoin(Paint.Join.ROUND);
	}

	protected void setPaint(float size, int red, int green, int blue) {
		paint.setStrokeWidth(size);
		paint.setARGB(255, red, green, blue);
		invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		centerX = getWidth() / 2;
		centerY = getHeight() / 2;
		canvas.drawCircle(centerX, centerY, paint.getStrokeWidth()/2, paint);
	}
}
