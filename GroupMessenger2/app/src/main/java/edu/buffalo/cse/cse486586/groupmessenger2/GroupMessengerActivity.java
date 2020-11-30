package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */

class SequenceComparator implements Comparator<String> {

    // Overriding compare()method of Comparator
    // for descending order of cgpa
    public int compare(String sq1, String sq2) {
        float sq1Parsed = Float.parseFloat(sq1);
        float sq2Parsed = Float.parseFloat(sq2);
        if (sq1Parsed - sq2Parsed < 0) {
            return -1;
        } else if (sq1Parsed - sq2Parsed > 0) {
            return 1;
        } else {
            return 0;
        }
    }
}

class MessageStateComparator implements Comparator<MessageState> {
    public int compare(MessageState m1, MessageState m2) {
        return new SequenceComparator().compare(m1.sequenceNumber, m2.sequenceNumber);
    }
}

class IntermediateStates {
    static String asked = "ASKFORPROPOSAL";
    static String finalized = "FINALIZED";
}

class MessageState {
    String id = "";
    String message = "";
    boolean isDeliverable = false;
    String sequenceNumber = "";
    String sourcePort = "";
    List<String> receivedProposals = new ArrayList<String>();
    String currentReceivedProposals = "";


    public MessageState(String id, String message, boolean isDeliverable, String sequenceNumber, String sourcePort) {
        this.id = id;
        this.message = message;
        this.isDeliverable = isDeliverable;
        this.sequenceNumber = sequenceNumber;
        this.sourcePort = sourcePort;
    }

    public void addProposal(String fromPort) {
        receivedProposals.add(fromPort);
    }

    public boolean areProposalsReceivedFromEveryone(Set<String> currentActiveProcesses) {
        int count = 0;
        for (String receivedProposal : receivedProposals) {
            if (currentActiveProcesses.contains(receivedProposal)) {
                count++;
            }
        }
        if (count == currentActiveProcesses.size()) {
            return true;
        }
        return false;
    }
}

public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static List<String> REMOTE_PORTS = new ArrayList<String>() {{
        add("11108");
        add("11112");
        add("11116");
        add("11120");
        add("11124");
    }};
    static final int SERVER_PORT = 10000;
    static String failedRemotePort = null;
    static final String KEY = "key";
    static final String VALUE = "value";
    static int receivedCount = 0;
    static String nextSequenceNo = null;
    static String myPort = "";
    static Map<String, Socket> mp = new HashMap<String, Socket>();
    static Set<String> msgSet = new HashSet<String>();
    static PriorityQueue<MessageState> pq = new PriorityQueue<MessageState>(2, new MessageStateComparator());
    static int populatedCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        nextSequenceNo = 1 + "." + myPort;
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        findViewById(R.id.button4).setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        // do something when the button is clicked
                        String msg = editText.getText().toString();
                        editText.setText("");
                        if (msg != null || msg != "") {
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                        }
                    }
                });

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String st = "";
                    Log.e(TAG + " server", st);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out =
                            new PrintWriter(clientSocket.getOutputStream(), true);
                    String receivedMsg = in.readLine();
                    String[] config = receivedMsg.split(",/,");
                    if (config[3].equals("asked")) {
                        Log.e(TAG + " server", " Asked " + config[2]);
                        // add to pq first after taking sequenceNo and increment it
                        MessageState msg = new MessageState(config[0], config[1], false, nextSequenceNo, config[2]);
                        pq.add(msg);
                        String[] nextSeq = nextSequenceNo.split("\\.");
                        System.out.println(nextSeq.length);
                        nextSequenceNo = ((Integer.parseInt(nextSeq[0])) + 1) + "." + myPort;

                        //propose new sequence back on publish progress after deciding count first
                        String msgToSend = msg.id + ",/," + msg.message + ",/," + msg.sequenceNumber;
                        out.println(msgToSend);
                    } else if (config[4].equals("finalized")) {

                        out.println("received");
                        MessageState msg1 = null;
                        for (MessageState msg : pq) {
                            if (msg.id.equals(config[0])) {
                                msg1 = msg;
                                pq.remove(msg);
                                break;
                            }
                        }
                        Log.e(TAG + " server", " finalized " + msg1.sourcePort);
                        msg1.sequenceNumber = config[2];
                        msg1.isDeliverable = true;
                        pq.add(msg1);

                        if (new SequenceComparator().compare(nextSequenceNo, msg1.sequenceNumber) < 1) {
                            nextSequenceNo = (Integer.parseInt(msg1.sequenceNumber.split("\\.")[0]) + 1) + "." + myPort;
                        }

                        while (pq.size() > 0) {
                            if (pq.peek().isDeliverable) {
                                MessageState msg2 = pq.poll();
                                Log.i(TAG + " server", msg2.message + " " + msg2.sourcePort);
                                String strReceived = msg2.message + " " + msg2.sourcePort;
                                publishProgress(strReceived, String.valueOf(populatedCount));
                                populatedCount++;
                            } else if (pq.peek().sourcePort.equals(failedRemotePort)) {
                                MessageState msg2 = pq.poll();
                                Log.i(TAG + " server s", " removing failed port message" + msg2.message + " " + msg2.sourcePort);
                            } else {
                                break;
                            }
                        }
                        // deliver it since it has been finalized remove it from the queue
                        // call publish progress
                    }
                } catch (SocketTimeoutException ste) {
                    System.out.println("Socket Read Timeout Exception");

                } catch (IOException ioe) {
                    System.out.println("Socket Connect IO Exception");

                } catch (Exception e) {
                    System.out.println(e.toString());
                }
                String currentPQStatus = "";
                for (MessageState msg : pq) {
                    if (currentPQStatus.equals(""))
                        currentPQStatus += "(" + msg.message + "," + msg.sourcePort + "," + msg.sequenceNumber + "," + msg.isDeliverable + ")";
                    else
                        currentPQStatus += " " + "(" + msg.message + "," + msg.sourcePort + "," + msg.sequenceNumber + "," + msg.isDeliverable + ")";
                }
                Log.i(TAG + " server", currentPQStatus);
            }
        }

        private Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            final TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(strings[0] + "\t\n");
            ContentValues keyValueToInsert = new ContentValues();
            Uri providerUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            keyValueToInsert.put(GroupMessengerActivity.KEY, String.valueOf(strings[1]));
            keyValueToInsert.put(GroupMessengerActivity.VALUE, strings[0]);
            Uri newUri = getContentResolver().insert(
                    providerUri,    // assume we already created a Uri object with our provider URI
                    keyValueToInsert
            );



            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            return;
        }
    }


    private class ClientTask extends AsyncTask<String, Void, Void> {
        String largestSequenceNumber = null;

        @Override
        protected Void doInBackground(String... msgs) {
            String largestSequenceNo = null;
            String msgToSend = msgs[0];
            String msgId = UUID.randomUUID().toString();
            Socket socket = null;
            Iterator<String> it = REMOTE_PORTS.iterator();

            while (it.hasNext()) {
                String remotePort = it.next();
                try {

                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    socket.setSoTimeout(2000);
                    PrintWriter out =
                            new PrintWriter(socket.getOutputStream(), true);
                    String msgSend = msgId + ",/," + msgToSend + ",/," + myPort + ",/," + "asked";
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    System.out.print(msgSend);
                    out.println(msgSend);
                    String inputLine = null;
                    inputLine = in.readLine();

                    Log.e(TAG + " client", "Proposal received back - asking");
                    String[] config = inputLine.split(",/,");
                    if (largestSequenceNo == null) {
                        largestSequenceNo = config[2];
                    } else {
                        int result = new SequenceComparator().compare(largestSequenceNo, config[2]);
                        if (result < 0) {
                            largestSequenceNo = config[2];
                        }
                    }

                    socket.close();
                    Thread.sleep(500);
                } catch(SocketException ske){
                    Log.e(TAG + " client", "socket Exception - asking" + remotePort);
                    failedRemotePort = remotePort;
                    Log.e(TAG + " client", "found at " + myPort + " for " + failedRemotePort);
                }catch (SocketTimeoutException skte) {
                    Log.e(TAG + " client", "socket timeout Exception - asking" + remotePort);
                    failedRemotePort = remotePort;
                    Log.e(TAG + " client", "found at " + myPort + " for " + failedRemotePort);
                } catch (NullPointerException e) {
                    Log.e(TAG + " client", "null pointer Exception - asking" + remotePort);
                    failedRemotePort = remotePort;
                    Log.e(TAG + " client", "found at " + myPort + " for " + failedRemotePort);
                } catch (Exception e) {
                    Log.e(TAG + " client", "generic Exception - asking" + remotePort);
                    failedRemotePort = remotePort;
                    Log.e(TAG + " client", "found at " + myPort + " for " + failedRemotePort);
                }
            }

            Log.e(TAG + " client", "all asks done" + " remote ports size : " + REMOTE_PORTS.size());
            Log.e(TAG + " client", "all asks done" + " failed port is :" + failedRemotePort);
            if (failedRemotePort != null) {
                Log.e(TAG + " client", "removing port : " + failedRemotePort);
                REMOTE_PORTS.remove(failedRemotePort);
            }
            Log.e(TAG + " client", "all asks done" + " remote ports size : " + REMOTE_PORTS.size());
            Log.e(TAG + " client", "all asks done" + " failed port is :" + failedRemotePort);

            Iterator<String> itr = REMOTE_PORTS.iterator();
            // send finalized
            while (itr.hasNext()) {
                String remotePort = itr.next();
                try {


                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    mp.put(remotePort, socket);
                    socket.setSoTimeout(2000);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));
                    PrintWriter out =
                            new PrintWriter(socket.getOutputStream(), true);
                    out.println(msgId + ",/," + msgToSend + ",/," + largestSequenceNo + ",/," + myPort + ",/," + "finalized");
                    String inputLine = null;
                    if (in.readLine() == null) {
                        throw new NullPointerException("finalized receive failed");
                    }
                    Log.e(TAG + " client", "Finalized sent and replied" + remotePort);

                    socket.close();
                    Thread.sleep(500);
                } catch(SocketException ske){
                    Log.e(TAG + " client", "socket Exception - asking" + remotePort);
                    failedRemotePort = remotePort;
                    Log.e(TAG + " client", "found at " + myPort + " for " + failedRemotePort);
                }catch (SocketTimeoutException skte) {
                    Log.e(TAG + " client", "socket timeout Exception - asking" + remotePort);
                    failedRemotePort = remotePort;
                    Log.e(TAG + " client", "found at " + myPort + " for " + failedRemotePort);
                } catch (NullPointerException e) {
                    Log.e(TAG + " client", "null pointer Exception - asking" + remotePort);
                    failedRemotePort = remotePort;
                    Log.e(TAG + " client", "found at " + myPort + " for " + failedRemotePort);
                } catch (Exception e) {
                    Log.e(TAG + " client", "generic exception - finalizing" + remotePort);
                    failedRemotePort = remotePort;
                    Log.e(TAG + " client", "found at " + myPort + " for " + failedRemotePort);
                }
            }
            Log.e(TAG + " client", "all finalized done" + " remote ports size : " + REMOTE_PORTS.size());
            Log.e(TAG + " client", "all finalized done" + " failed port is :" + failedRemotePort);
            if (failedRemotePort != null) {
                Log.e(TAG + " client", "removing port : " + failedRemotePort);
                REMOTE_PORTS.remove(failedRemotePort);
            }
            Log.e(TAG + " client", "all finalized done" + " remote ports size : " + REMOTE_PORTS.size());
            Log.e(TAG + " client", "all finalized done" + " failed port is :" + failedRemotePort);
            return null;
        }
    }


}

