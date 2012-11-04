package hurdad.scribble;

import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class ServerListActivity extends ListActivity {

    // Return Intent extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    // Member fields
    private BluetoothAdapter bluetoothAdapter;
	private static ListView selectServerListView;
	private static ArrayAdapter<String> serverAdapter;
	private static ArrayList<String> servers;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.select_server_dialog);

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        serverAdapter = new ArrayAdapter<String>(this, R.layout.select_server_list_item);
        selectServerListView = getListView();
        selectServerListView.setAdapter(serverAdapter);
        selectServerListView.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> adapterView, View itemView, int position, long id) {
				bluetoothAdapter.cancelDiscovery();

	            // Get the device MAC address, which is the last 17 chars in the View
	            String info = ((TextView) itemView).getText().toString();
	            String address = info.substring(info.length() - 17);

	            // Create the result Intent and include the MAC address
	            Intent intent = new Intent();
	            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

	            // Set result and finish this Activity
	            setResult(Activity.RESULT_OK, intent);
	            finish();
			}
        });
        
        servers = new ArrayList<String>();
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				servers.add(device.getName() + "\n" + device.getAddress());
				serverAdapter.add(device.getName() + "\n" + device.getAddress());
			}
		}

        // Initialize the button to perform device discovery
        Button scanButton = (Button) findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                discoverServers();
                v.setVisibility(View.GONE);
            }
        });
	}
	
	private void discoverServers() {

        // Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true);

        // If we're already discovering, stop it
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        bluetoothAdapter.startDiscovery();
	}

	public static class DeviceFoundReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// get the bluetooth device
			BluetoothDevice server = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			
			if (!servers.contains(server)) {
				// add to the server listView
				serverAdapter.add(server.getName() + "\n" + server.getAddress());
				serverAdapter.notifyDataSetChanged();
				selectServerListView.invalidate();
			}
		}
	}

}
