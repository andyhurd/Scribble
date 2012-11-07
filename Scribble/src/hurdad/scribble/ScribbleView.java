package hurdad.scribble;

import java.util.ArrayList;
import java.util.Stack;

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
	private static final int PATH_END = 2;
	private static final int PATH_CLEAR = 3;
	private static final int FULL_CLEAR = 4;
	private static final int BRUSH_CHANGE = 5;

	private Handler handler;

	private Paint localPaint;
	private int localRed = 0;
	private int localGreen = 0;
	private int localBlue = 0;

	private Paint remotePaint;
	private int remoteRed = 0;
	private int remoteGreen = 0;
	private int remoteBlue = 0;

	private Stack<Path> localPaths;
	private Stack<Paint> localPaints;
	private boolean localPathSaved = false;

	private Stack<Path> remotePaths;
	private Stack<Paint> remotePaints;
	private boolean remotePathSaved = false;

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

	public ScribbleView(Context context, AttributeSet attrs) {
		super(context, attrs);

		localPaths = new Stack<Path>();
		remotePaths = new Stack<Path>();

		localPaints = new Stack<Paint>();
		remotePaints = new Stack<Paint>();

		localPaint = new Paint();
		localPaint.setColor(Color.BLACK);
		localPaint.setStyle(Paint.Style.STROKE);
		localPaint.setStrokeWidth(8.0f);
		localPaint.setAntiAlias(true);
		localPaint.setStrokeJoin(Paint.Join.ROUND);

		remotePaint = new Paint(localPaint);
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

			localPaint = new Paint(localPaint);
			localPath = new Path();
			localPathSaved = false;

			localPath.moveTo(x, y);
			lastX = x;
			lastY = y;
			Float[] point = new Float[2];
			point[0] = x;
			point[1] = y;
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

			Float[] points = pointsBuf.toArray(new Float[pointsBuf.size()]);
			handler.obtainMessage(Scribble.MESSAGE_WRITE, PATH_MOVE, -1, points).sendToTarget();

			// After replaying history, connect the line to the touch point.
			localPath.lineTo(x, y);

			float halfStrokeWidth = localPaint.getStrokeWidth() / 2;

			// schedules a repaint
			invalidate((int) (leftBound - halfStrokeWidth),
					(int) (topBound - halfStrokeWidth),
					(int) (rightBound + halfStrokeWidth),
					(int) (bottomBound + halfStrokeWidth));

			lastX = x;
			lastY = y;
			
			if (event.getAction() == MotionEvent.ACTION_UP) {
				Log.d("foo", "SENDING PATH_END");
				localPaths.push(localPath);
				localPaints.push(localPaint);
				localPathSaved = true;
				handler.obtainMessage(Scribble.MESSAGE_WRITE, PATH_END, -1, new Float[0]).sendToTarget();
			}

			return true;

		default:
			return false;
		}
	}

	public void drawRemote(int pathStatus, float[] points) {
		if (pathStatus == PATH_CLEAR) {
			remoteClear();
		} else if (pathStatus == PATH_START) {

			remotePaint = new Paint(remotePaint);
			remotePath = new Path();
			remotePathSaved = false;
			
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

			// After replaying history, connect the line to the touch point.
			remotePath.lineTo(x, y);

			float halfStrokeWidth = localPaint.getStrokeWidth() / 2;

			// schedules a repaint
			invalidate((int) (leftBound - halfStrokeWidth),
					(int) (topBound - halfStrokeWidth),
					(int) (rightBound + halfStrokeWidth),
					(int) (bottomBound + halfStrokeWidth));

			remoteLastX = x;
			remoteLastY = y;
		} else if (pathStatus == PATH_END) {
			Log.d("foo", "path end");
			remotePaths.push(remotePath);
			remotePaints.push(remotePaint);
			remotePathSaved = true;
		}
	}

	public void remoteClear() {

		if (remotePaths.size() > 0) {

			// now can undo the path before the one just removed
			remotePaths.pop();
		}
		if (remotePaints.size() > 0) {

			// discard associated paint with that path
			remotePaints.pop();
		}

		// repaint the view
		invalidate();
	}

	public void clear() {

		if (localPaths.size() > 0) {

			// now can undo the path before the one just removed
			localPaths.pop();
		}
		if (localPaints.size() > 0) {

			// discard associated paint with that path
			localPaints.pop();
		}

		handler.obtainMessage(Scribble.MESSAGE_WRITE, PATH_CLEAR, -1, new Float[0])
				.sendToTarget();

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
		canvas.drawColor(Color.WHITE);

		for (int i = 0; i < localPaths.size(); i++) {
			canvas.drawPath(localPaths.get(i), localPaints.get(i));
		}
		if (localPath != null && !localPathSaved) {
			canvas.drawPath(localPath, localPaint);
		}

		for (int i = 0; i < remotePaths.size(); i++) {
			canvas.drawPath(remotePaths.get(i), remotePaints.get(i));
		}
		if (remotePath != null) {
			canvas.drawPath(remotePath, remotePaint);
		}
	}

	public float getPaintStrokeWidth() {
		return localPaint.getStrokeWidth();
	}

	public int[] getPaintRGB() {
		int[] rgb = new int[3];
		rgb[0] = localRed;
		rgb[1] = localGreen;
		rgb[2] = localBlue;
		return rgb;
	}

	public void setPaint(float size, int red, int green, int blue) {

		localPaint = new Paint(localPaint);

		this.localRed = red;
		this.localGreen = green;
		this.localBlue = blue;
		localPaint.setARGB(255, red, green, blue);
		localPaint.setStrokeWidth(size);
	}
}
