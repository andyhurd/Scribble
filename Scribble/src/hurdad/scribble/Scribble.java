package hurdad.scribble;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

public class Scribble extends Activity {
	
    // message types send from connection manager
    protected static final int MESSAGE_STATE_CHANGE = 1;
	protected static final int MESSAGE_READ = 2;
	protected static final int MESSAGE_WRITE = 3;
	protected static final int MESSAGE_TOAST = 4;
    
	// intent request codes
	private final int REQUEST_CONNECT_DEVICE = 0;
	private final int REQUEST_ENABLE_BLUETOOTH = 1;
	private final int REQUEST_BRUSH_MODIFY = 2;
	
	protected static final String DEVICE_NAME = "device_name";
	protected static final String TOAST = "toast";

	private ByteBuffer outStreamBuffer;
	private static BluetoothAdapter bluetoothAdapter;
	private ScribbleConnectionManager connectionManager;
	
	// dialog layout and backing data
	private static ScribbleView scribbleView;
	
	// main activity layout elements
	private Button connectButton;
	
    // *************************************************************************
    // LIFE CYCLE FUNCTIONS
	//
    // *************************************************************************
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scribble);
        
        // get a handle for the drawable canvas and give it local handler for communication
        scribbleView = (ScribbleView) findViewById(R.id.scribbleView);
        scribbleView.setHandler(handler);

        // get handle for connect button on the bottom of the screen
        connectButton = (Button) findViewById(R.id.connectButton);
        
        // get the bluetoothAdapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
	protected void onStart() {
		super.onStart();
		
		// if bluetooth is available, but not enabled, prompt the user to enable
		if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
			
			// starts default intent to enable bluetooth
    		Intent intentEnableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    		startActivityForResult(intentEnableBluetooth, REQUEST_ENABLE_BLUETOOTH);
    		
		} else {
			
			// if the connectionManager is null, create it
			if (connectionManager == null) {
				setupConnection();
			}
		}
	}
    
    @Override
	protected void onResume() {
        super.onResume();
        
        if (connectionManager != null) {

        	// if the connectionManager has state NONE, need to start the connection process
            if (connectionManager.getState() == ScribbleConnectionManager.STATE_NONE) {
            	
            	// listen() - server function that blocks to accept a connection
            	connectionManager.listen();
            }
        }
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		// stop all connection threads on exit
		if (connectionManager != null) {
			connectionManager.stop();
		}
	}

    // *************************************************************************
    // CONNECTION FUNCTIONS
	//
    // *************************************************************************

	private void setupConnection() {

        // Initialize the BluetoothChatService to perform bluetooth connections
        connectionManager = new ScribbleConnectionManager(handler);

        // initialize the buffer for outgoing messages
        outStreamBuffer = ByteBuffer.allocate(128);
    }
	
	private void allowDiscoverable() {
		
		// if the device isn't currently broadcasting its presence, prompt the user to allow discoverable
		if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			startActivity(discoverableIntent);
		}
	}

    // *************************************************************************
    // RESULT AND MESSAGE HANDLERS
	//
    // *************************************************************************

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		if (requestCode == REQUEST_CONNECT_DEVICE) {
			if (resultCode == Activity.RESULT_OK) {
				String address = data.getExtras().getString(ServerListActivity.EXTRA_DEVICE_ADDRESS);
				BluetoothDevice server = bluetoothAdapter.getRemoteDevice(address);
				connectionManager.connect(server);
			}
		} else if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
			if (resultCode == Activity.RESULT_OK) {
				setupConnection();
				
			} else {
				// bluetooth wasn't enabled, so exit the activity
				finish();
			}
			
		} else if (requestCode == REQUEST_BRUSH_MODIFY) {
			if (resultCode == Activity.RESULT_OK) {
				float size = data.getFloatExtra("size", 8f);
				int red = data.getIntExtra("red", 0);
				int green = data.getIntExtra("green", 0);
				int blue = data.getIntExtra("blue", 0);
				scribbleView.setPaint(size, red, green, blue);
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}
	
    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
        	
        	if (message.what == MESSAGE_STATE_CHANGE) {		// the connection status has changed, update UI
        		
        		if (message.arg1 == ScribbleConnectionManager.STATE_CONNECTED) {
        			connectButton.setText("connected");
        			scribbleView.clear();
        		} else if (message.arg1 == ScribbleConnectionManager.STATE_CONNECTING) {
        			connectButton.setText("connecting");
        		} else {
        			connectButton.setText("no conn");
        		}
        		
        	} else if (message.what == MESSAGE_WRITE) {		// this device has drawn, writing to inform peer

                // only write if connected
                if (connectionManager.getState() != ScribbleConnectionManager.STATE_CONNECTED) {
                    return;
                }
                
        		// get the path status (whether this data is a PATH_START, PATH_MOVE, or PATH_CLEAR event)
        		int pathStatus = message.arg1;
        		
        		// get points to send
        		Float[] points = (Float[]) message.obj;
        		
        		// build float array
        		float[] floats = new float[points.length];
        		for (int i = 0; i < points.length; i++) {
        			floats[i] = points[i];
        		}
        		
        		// wrap floats in a float buffer for easy creation of byte buffer
        		FloatBuffer floatBuffer = FloatBuffer.wrap(floats);
        		
        		// allocated enough space for the floats, plus the two int flags (pathStatus, capacity)
        		outStreamBuffer = ByteBuffer.allocate(8 + floatBuffer.capacity() * 4);
        		
        		// write the flags and data to the out Buffer
        		outStreamBuffer.putInt(pathStatus);
        		outStreamBuffer.putInt(floats.length);
        		outStreamBuffer.asFloatBuffer().put(floatBuffer);
        		
        		if (pathStatus == 2) {
        			Log.d("FOO", "WRITING PATH_END");
        		}
        		
        		// obtain byte[] from buffer and write to socket
        		byte[] bytes = outStreamBuffer.array();
        		connectionManager.write(bytes);
        		outStreamBuffer.clear();
        		
        	} else if (message.what == MESSAGE_READ) {		// the other device has drawn, need to update locally
        		
        		// cast the message read in the connectionManager to a byte[], then wrap in ByteBuffer
        		byte[] readBuf = (byte[]) message.obj;
        		ByteBuffer byteBuffer = ByteBuffer.wrap(readBuf);
        		
        		// determine the pathStatus (all reads have path data, either PATH_START, PATH_MOVE, PATH_CLEAR)
        		int pathStatus = byteBuffer.getInt();
        		
        		// determine the number of floats to receive (sent in x-y pairs, {x1, y1, x2, y2, x3, ..})
        		int capacity = byteBuffer.getInt();
        		
        		// use the capacity found to initialize points array and write points to it
        		float[] points = new float[capacity];
        		for (int i = 0; i < capacity; i++) {
        			points[i] = byteBuffer.getFloat();
        		}
        		
        		// tell the scribble view canvas to draw the received path
        		scribbleView.drawRemote(pathStatus, points);
        		
        	} else if (message.what == MESSAGE_TOAST) {
        		
        		// allows for printing connection errors to activity
        		Toast.makeText(Scribble.this, message.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
        	}
        }
    };

    // *************************************************************************
    //
    // MENU FUNCTIONS
	//
    // *************************************************************************

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_scribble, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	int itemId = item.getItemId();
    	if (itemId == R.id.clear) {
    		
    		// clear locally-drawn path
    		((ScribbleView) findViewById(R.id.scribbleView)).clear();
    		return true;
    		
    	} else if (itemId == R.id.brush) {
    		
    		// get params to send to BrushModifyActivity
    		float size = scribbleView.getPaintStrokeWidth();
    		int[] rgb = scribbleView.getPaintRGB();
    		
			// starts default intent to enable bluetooth
    		Intent intent = new Intent(this, BrushModifyActivity.class);
    		intent.putExtra("size", size);
    		intent.putExtra("red", rgb[0]);
    		intent.putExtra("green", rgb[1]);
    		intent.putExtra("blue", rgb[2]);
    		startActivityForResult(intent, REQUEST_BRUSH_MODIFY);
    		
    	} else if (itemId == R.id.scan) {
    		
            // prompt the user to select the server they would like to connect to
            Intent serverIntent = new Intent(this, ServerListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
            
    	} else if (itemId == R.id.discoverable) {

    		// allow other devices to discover this device
            allowDiscoverable();
            return true;
    	}
        return false;
    }
}
