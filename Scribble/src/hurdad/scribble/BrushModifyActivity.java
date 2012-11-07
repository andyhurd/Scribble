package hurdad.scribble;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class BrushModifyActivity extends Activity {
	
	BrushPreview brushPreview;
	
	SeekBar brushSizeSeekBar;
	SeekBar redSeekBar;
	SeekBar greenSeekBar;
	SeekBar blueSeekBar;
	
	float size;
	int red;
	int green;
	int blue;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        if (getIntent() != null) {
        	size = getIntent().getFloatExtra("size", 8.0f);
        	red = getIntent().getIntExtra("red", 0);
        	green = getIntent().getIntExtra("green", 0);
        	blue = getIntent().getIntExtra("blue", 0);
        } else {
        	size = 0f;
        	red = 0;
        	green = 0;
        	blue = 0;
        }
        	
        setContentView(R.layout.brush_menu);
		
		// get a reference to brush preview element
		brushPreview = (BrushPreview) findViewById(R.id.brushPreview);
		brushPreview.setPaint(size, red, green, blue);
		
		// get a handle on all seek bars in view
		brushSizeSeekBar = (SeekBar) findViewById(R.id.brushSizeSeekBar);
		redSeekBar = (SeekBar) findViewById(R.id.redSeekBar);
		greenSeekBar = (SeekBar) findViewById(R.id.greenSeekBar);
		blueSeekBar = (SeekBar) findViewById(R.id.blueSeekBar);
		
		// set on seek bar changed handling
		brushSizeSeekBar.setOnSeekBarChangeListener(brushChangedListener);
		redSeekBar.setOnSeekBarChangeListener(brushChangedListener);
		greenSeekBar.setOnSeekBarChangeListener(brushChangedListener);
		blueSeekBar.setOnSeekBarChangeListener(brushChangedListener);
	}
	
	@Override
	public void onBackPressed() {
		
		// return paint changes on back press
		Intent intent = new Intent();
		intent.putExtra("size", size);
		intent.putExtra("red", red);
		intent.putExtra("green", green);
		intent.putExtra("blue", blue);
		setResult(RESULT_OK, intent);
		
		super.onBackPressed();
	}

	OnSeekBarChangeListener brushChangedListener = new OnSeekBarChangeListener() {

		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			
			if (seekBar.equals(brushSizeSeekBar)) {
				BrushModifyActivity.this.size = progress;
				
			} else if (seekBar.equals(redSeekBar)) {
				BrushModifyActivity.this.red = progress;
				
			} else if (seekBar.equals(greenSeekBar)) {
				BrushModifyActivity.this.green = progress;
				
			} else if (seekBar.equals(blueSeekBar)) {
				BrushModifyActivity.this.blue = progress;
			}
			brushPreview.setPaint(size, red, green, blue);
		}

		public void onStartTrackingTouch(SeekBar seekBar) {}
		public void onStopTrackingTouch(SeekBar seekBar) {}
	};

}
