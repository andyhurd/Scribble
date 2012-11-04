package hurdad.scribble;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ScribbleView extends View {
	private static final int PATH_START = 0;
	private static final int PATH_MOVE = 1;
	private static final int PATH_CLEAR = 2;
	
	private Handler handler;
	private Paint paint;
	private Path localPath;
	private Path remotePath;
	
	private float lastX;
	private float lastY;
	private float remoteLastX;
	private float remoteLastY;

	private float leftBound;
	private float rightBound;
	private float topBound;
	private float bottomBound;

	private float remoteLeftBound;
	private float remoteRightBound;
	private float remoteTopBound;
	private float remoteBottomBound;
	
	private final float STROKE_WIDTH = 8.0f;
	private final float HALF_STROKE_WIDTH = STROKE_WIDTH / 2;
	
	public ScribbleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint = new Paint();
		localPath = new Path();
		remotePath = new Path();
		paint.setColor(Color.WHITE);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(STROKE_WIDTH);
		paint.setAntiAlias(true);
		paint.setStrokeJoin(Paint.Join.ROUND);
	}
	
	public void setHandler(Handler handler) {
		this.handler = handler;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float x = event.getX();
		float y = event.getY();
		
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				localPath.moveTo(x, y);
				lastX = x;
				lastY = y;
				Float[] point = new Float[2];
				point[0] = x;
				point[1] = y;
				Log.d("FOO", "SENDING MESSAGE_WRITE");
				handler.obtainMessage(Scribble.MESSAGE_WRITE, PATH_START, -1, point).sendToTarget();
				return true;
			case MotionEvent.ACTION_MOVE:
			case MotionEvent.ACTION_UP:
				
				resetInvalidateRect(x, y);
				ArrayList<Float> pointsBuf = new ArrayList<Float>();
				
				int historySize = event.getHistorySize();
				for (int i = 0; i < historySize; i++) {
					float historicalX = event.getHistoricalX(i);
					float historicalY = event.getHistoricalY(i);
					
					pointsBuf.add(historicalX);
					pointsBuf.add(historicalY);
					
					adjustInvalidateRect(historicalX, historicalY);
					localPath.lineTo(historicalX, historicalY);
				}
				pointsBuf.add(x);
				pointsBuf.add(y);

				Log.d("FOO", "SENDING MESSAGE_WRITE");
				Float[] points = pointsBuf.toArray(new Float[pointsBuf.size()]);
				handler.obtainMessage(Scribble.MESSAGE_WRITE, PATH_MOVE, -1, points).sendToTarget();

		        // After replaying history, connect the line to the touch point.
				localPath.lineTo(x, y);
				
				// schedules a repaint
				invalidate(
						(int)(leftBound - HALF_STROKE_WIDTH),
						(int)(topBound - HALF_STROKE_WIDTH),
						(int)(rightBound + HALF_STROKE_WIDTH),
						(int)(bottomBound + HALF_STROKE_WIDTH));
				
				lastX = x;
				lastY = y;
				
				return true;
				
			default:
				return false;
		}
	}
	
	public void drawRemote(int pathStatus, float[] points) {
		Log.d("drawRemote", "drawRemote");
		if (pathStatus == PATH_CLEAR) {
			Log.d("foo", "remote clear");
			remoteClear();
		} else if (pathStatus == PATH_START) {
			float x = points[0];
			float y = points[1];
			remotePath.moveTo(x, y);
			remoteLastX = x;
			remoteLastY = y;
		} else if (pathStatus == PATH_MOVE) {
			float x = points[points.length - 2];
			float y = points[points.length - 1];
			resetRemoteInvalidateRect(x, y);
			for (int i = 0; i < points.length; i++) {
				
				x = points[i];
				y = points[i + 1];
				
				adjustInvalidateRect(x, y);
				remotePath.lineTo(x, y);
				
				// increment by two
				i++;
			}
			
			// schedules a repaint
			invalidate(
					(int)(remoteLeftBound - HALF_STROKE_WIDTH),
					(int)(remoteTopBound - HALF_STROKE_WIDTH),
					(int)(remoteRightBound + HALF_STROKE_WIDTH),
					(int)(remoteBottomBound + HALF_STROKE_WIDTH));
			
			remoteLastX = x;
			remoteLastY = y;
		}
	}
	
	public void remoteClear() {
		Log.d("FOO", "REMOTE CLEAR");
		remotePath.reset();
		
		// repaint the view
		invalidate();
	}
	
	public void clear() {
		localPath.reset();
		
		Float[] point = new Float[0];
		handler.obtainMessage(Scribble.MESSAGE_WRITE, PATH_CLEAR, -1, point).sendToTarget();
		
		// repaint the view
		invalidate();
	}
	
	public void resetRemoteInvalidateRect(float x, float y) {
		remoteLeftBound = Math.min(remoteLastX, x);
		remoteRightBound = Math.max(remoteLastX, x);
		remoteTopBound = Math.min(remoteLastY, y);
		remoteBottomBound = Math.max(remoteLastY, y);
	}
	
	public void resetInvalidateRect(float x, float y) {
		leftBound = Math.min(lastX, x);
		rightBound = Math.max(lastX, x);
		topBound = Math.min(lastY, y);
		bottomBound = Math.max(lastY, y);
	}
	
	public void adjustRemoteInvalidateRect(float x, float y) {
		remoteLeftBound = Math.min(remoteLeftBound, x);
		remoteRightBound = Math.max(remoteRightBound, x);
		remoteTopBound = Math.min(remoteTopBound, y);
		remoteBottomBound = Math.max(remoteBottomBound, y);
	}
	
	public void adjustInvalidateRect(float x, float y) {
		leftBound = Math.min(leftBound, x);
		rightBound = Math.max(rightBound, x);
		topBound = Math.min(topBound, y);
		bottomBound = Math.max(bottomBound, y);
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawColor(Color.GREEN);
		canvas.drawPath(localPath, paint);
		canvas.drawPath(remotePath, paint);
	}
}
