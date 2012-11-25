package hurdad.scribble;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class ScribbleConnectionManager {
	private final UUID SCRIBBLE_UUID = UUID.fromString("67520b31-28b2-4b26-8218-ae82a517807e");
    private static final String APP_NAME = "Scribble";
    private static final String LOG_TAG = "ScribbleConnectionManager";
    private static final int BUFFER_SIZE = 128;
	
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
	private AcceptThread acceptThread;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;
	private int state;

    // constants for indicating current connection state
    public static final int STATE_NONE = 0;       // doing nothing
    public static final int STATE_LISTEN = 1;     // listening for connections
    public static final int STATE_CONNECTING = 2; // initiating connection
    public static final int STATE_CONNECTED = 3;  // connected
    
    // constants for indicating message to display
    public static final int UNABLE_TO_CONNECT = 0;
    public static final int CONNECTION_WAS_LOST = 1;
    
    // *************************************************************************
    // CONSTRUCTOR
	//
    // *************************************************************************
	
    public ScribbleConnectionManager(Handler handler) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
        this.handler = handler;
    }

    // *************************************************************************
    // STATE TRANSITION FUNCTIONS
	//
    // *************************************************************************
	
    /**
     * start listening for connections
     */
    public synchronized void listen() {

        // cancel connecting thread
        if (connectThread != null) {
        	connectThread.cancel();
        	connectThread = null;
        }

        // cancel connected thread
        if (connectedThread != null) {
        	connectedThread.cancel();
        	connectedThread = null;
        }

        // start listening on socket
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
        
        // update state to listening
        setState(STATE_LISTEN);
    }
    
    /**
     * establish a connection with a given BluetoothDevice
     * @param server	The BluetoothDevice to connect to
     */
    public synchronized void connect(BluetoothDevice server) {

        // if already trying to connect, override that attempt
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
            	connectThread.cancel();
            	connectThread = null;
            }
        }

        // if already connected, drop that connection and focus on this connect attempt
        if (connectedThread != null) {
        	connectedThread.cancel();
        	connectedThread = null;
        }

        // start thread to connect to the server device
        connectThread = new ConnectThread(server);
        connectThread.start();
        
        // update state to connecting
        setState(STATE_CONNECTING);
    }

    /**
     * start the connected thread for dealing with transmissions
     * @param socket	The BluetoothSocket for this connection
     */
    public synchronized void communicate(BluetoothSocket socket) {

        // stop connecting thread
        if (connectThread != null) {
        	connectThread.cancel();
        	connectThread = null;
        }

        // stop any current connection
        if (connectedThread != null) {
        	connectedThread.cancel();
        	connectedThread = null;
        }

        // stop accepting connections
        if (acceptThread != null) {
        	acceptThread.cancel();
        	acceptThread = null;
        }

        // start the new connected thread
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        // update state to connected
        setState(STATE_CONNECTED);
    }
    
    // *************************************************************************
    // STOP - remove all threads
	//
    // *************************************************************************
	
    /**
     * stop all threads -- revert to doing nothing
     */
    public synchronized void stop() {
    	// stop any connecting thread
        if (connectThread != null) {
        	connectThread.cancel();
        	connectThread = null;
        }
        
        // stop any connected thread
        if (connectedThread != null) {
        	connectedThread.cancel();
        	connectedThread = null;
        }
        // stop any accepting thread
        if (acceptThread != null) {
        	acceptThread.cancel();
        	acceptThread = null;
        }
        
        // revert state to doing nothing
        setState(STATE_NONE);
    }
    
    // *************************************************************************
    // WRITE - outgoing data
	//
    // *************************************************************************
	
    /**
     * write to the connected thread
     * @param out		The bytes to write
     */
    public void write(byte[] out) {
        ConnectedThread tempThread;

        // synchronize on a copy of the connected thread
        synchronized (this) {
            if (state != STATE_CONNECTED) return;
            tempThread = connectedThread;
        }
        
        // write unsynchronized
        tempThread.write(out);
    }
    
    // *************************************************************************
    // CONNECTION STATE
	//
    // *************************************************************************
    
    /**
     * update the connection state
     * @param state
     */
    private synchronized void setState(int state) {
    	this.state = state;
		
        // allow the UI to update to reflect state change
        handler.obtainMessage(Scribble.MESSAGE_STATE_CHANGE, this.state, -1).sendToTarget();
    }
    
    /**
     * get the current state of the connection
     * @return
     */
    public synchronized int getState() {
        return state;
    }

    // *************************************************************************
    // ACCEPT THREAD
	//
    // *************************************************************************
	
	private class AcceptThread extends Thread {
		private final BluetoothServerSocket listenSocket;
		
		public AcceptThread() {
			// since listenSocket is final, use a temp variable then assign
			BluetoothServerSocket tempSocket = null;
			try {
				// use app UUID, will also be used by client
				tempSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, SCRIBBLE_UUID);
			} catch (IOException e) {
				Log.e(LOG_TAG, "acceptThread: constructor: listen() failed", e);
			}
			listenSocket = tempSocket;
		}
		
		@Override
		public void run() {
			BluetoothSocket socket = null;
			
			// listen until exception or have a socket
			while (state != STATE_CONNECTED) {
				try {
					socket = listenSocket.accept();
				} catch (IOException e) {
					Log.e(LOG_TAG, "acceptThread: run(): accept() failed", e);
					break;
				}
				
				// if a connection is accepted
				if (socket != null) {
					synchronized(ScribbleConnectionManager.this) {
						if (state == STATE_LISTEN || state == STATE_CONNECTING) {
							// should be the condition when attempting to establish connection
							communicate(socket);
						} else {
							// either skipped a state, or already connected, don't use this socket
							try {
								socket.close();
							} catch (IOException e) {
								Log.e(LOG_TAG, "acceptThread: run(): unwanted socket close() failed", e);
							}
						}
					}
				}
			}
		}
		
		public void cancel() {
			try {
				listenSocket.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "acceptThread: cancel(): listening socket close() failed", e);
			}
		}
	}

    // *************************************************************************
    // CONNECT THREAD
	//
    // *************************************************************************
	
	private class ConnectThread extends Thread {
		private final BluetoothSocket socket;
		
		public ConnectThread(BluetoothDevice server) {
			// use temp BluetoothSocket then assign to final socket
			BluetoothSocket tempSocket = null;
			
			// get a socket to connect to server
			try {
				// use SCRIBBLE_UUID common to server
				tempSocket = server.createRfcommSocketToServiceRecord(SCRIBBLE_UUID);
			} catch (IOException e) {
				Log.e(LOG_TAG, "connectThread: constructor: socket() failed", e);
			}
			socket = tempSocket;
		}
		
		public void run() {
			// remove discovery
			bluetoothAdapter.cancelDiscovery();
			
			try {
				// blocking connect to the socket
				socket.connect();
				
			} catch (IOException connectException) {
				connectionFailed();

				// unable to connect, close the socket
				try {
					socket.close();
				} catch (IOException closeException) {
					Log.e(LOG_TAG, "connectThread: run(): close() failed", closeException);
				}
				
				// restart connection process
				listen();
				return;
			}
			
			// remove access to this connectThread
			synchronized(ScribbleConnectionManager.this) {
				connectThread = null;
			}
			
			// execute connected app thread
			communicate(socket);
		}
		
		public void cancel() {
			try {
				socket.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "connectThread: cancel(): close() failed", e);
			}
		}
	}

    // *************************************************************************
    // CONNECTED THREAD
	//
    // *************************************************************************
	
	private class ConnectedThread extends Thread {
		private final BluetoothSocket socket;
		private final InputStream inStream;
		private final OutputStream outStream;
		
		public ConnectedThread(BluetoothSocket socket) {
			this.socket = socket;
			
			// attempt to obtain in/out streams to temp var first
			InputStream tempIn = null;
			OutputStream tempOut = null;
			
			// get the socket input and output streams
			try {
				tempIn = socket.getInputStream();
				tempOut = socket.getOutputStream();
			} catch (IOException e) {
				Log.e("LOG_TAG", "connectedThread: constructor: retrieve in/outStream failed", e);
			}
			
			// update final local vars
			inStream = tempIn;
			outStream = tempOut;
		}
		
		@Override
		public void run() {
			// buffer to read in from
			byte[] buffer = new byte[BUFFER_SIZE];
			
			// number of bytes returned from read()
			int bytes;
			
			while (true) {
				try {
					// read from the input stream
					bytes = inStream.read(buffer);
					
					// send obtained bytes to the main thread
                    handler.obtainMessage(Scribble.MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    
				} catch (IOException e) {
					
					// if failed, inform user and revert to listening
					Log.e(LOG_TAG, "connectedThread: run(): connection lost", e);
					connectionLost();
					break;
				}
			}
		}
		
		// sends data to peer
		public void write(byte[] bytes) {
			try {
				outStream.write(bytes);
			} catch (IOException e) {
				Log.e(LOG_TAG, "connectedThread: write() failed", e);
			}
		}
		
		// cancel the communicating thread by closing its socket
		public void cancel() {
			try {
				socket.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "connectedThread: cancel(): close() failed", e);
			}
		}
	}

    // *************************************************************************
    // CONNECTION FAILURE HANDLERS
	//
    // *************************************************************************

    private void connectionFailed() {
    	// revert to listening state
        setState(STATE_LISTEN);

        // inform user
        toast(UNABLE_TO_CONNECT);
    }

    private void connectionLost() {
    	// revert to listening state
        setState(STATE_LISTEN);

        // inform user
        toast(CONNECTION_WAS_LOST);
    }
    
    private void toast(int toast_id) {
        Message message = handler.obtainMessage(Scribble.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putInt(Scribble.TOAST, toast_id);
        message.setData(bundle);
        handler.sendMessage(message);
    }

}
