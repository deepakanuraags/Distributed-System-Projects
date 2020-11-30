package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

class NodeComparator implements Comparator<Node> {
    public int compare(Node n1, Node n2) {
        return n1.getId().compareTo(n2.getId());
    }
}

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtActivity.class.getSimpleName();
    static String MainPort = "11108";
    static final int SERVER_PORT = 10000;
    static String myPort;
    static Node myNode;
    List<Node> currentNodes = new ArrayList<Node>();

    private String getAllLocalRecords(){
        Log.i(TAG,"getting all local records" + " " + myNode.getPortNum());
        String result = "";
        int count = 0;
        for(String file : getContext().fileList()){
            Log.i(TAG,"getting local record for all local records" + file);
            count++;
            try{
                FileInputStream fis = getContext().openFileInput(file);
                InputStreamReader inputStreamReader =
                        new InputStreamReader(fis, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader);
                String value = reader.readLine();
                Log.i(TAG,"read key: " + file);
                if(result.equals("")){
                    result += file + "-" + value;
                }else{
                    result += "/" + file + "-" + value;
                }
                Log.i(TAG,"Total Read Count at Local : " + count + " at port " + myNode.getPortNum());

            }catch (Exception e){
                Log.e(TAG + " provider", e.getMessage());
            }
        }
        return result;
    }

    private String getRecordsFromSuccessiveNodes(String sourcePort){
        Log.i(TAG,"getting all records from successive node" + " " + myNode.getNextPortNum());
        String result="";
        try {
            Socket socket=null;
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(myNode.getNextPortNum()));
            PrintWriter out =
                    new PrintWriter(socket.getOutputStream(), true);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out.println("REQUEST_ALL_VALUES" + "/" + sourcePort);
            Log.i(TAG,"waiting for results from next node");
            result = in.readLine();

            String[] totalResult= result.split("/");
            Log.i(TAG,"total records received at " + myNode.getPortNum() + " " + totalResult.length);
            Log.i(TAG,"result of all values" + result);
        }
        catch (Exception e){
            Log.e(TAG, "ClientTask socket Exception");
        }
        return result;
    }

    private boolean checkLocalPossiblity(String key){
        if(myNode.getId().compareTo(myNode.getPrevId()) > 0)
        {
            if(key.compareTo(myNode.getId()) <=0 && key.compareTo(myNode.getPrevId()) > 0){
                return true;
            }
        }
        else if(myNode.getId().compareTo(myNode.getPrevId()) < 0 )
        {
            if(key.compareTo(myNode.getId())<=0 || key.compareTo(myNode.getPrevId()) > 0 ){
                return true;
            }
        }
        return false;
    }

    public void localWrite(String filename,String value){
        try {
            FileOutputStream outputStream;
            Log.i(TAG,"local writing key :" + filename);
            outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(value.getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG + " provider", "File write failed insert");
        }
    }

    public void forwardWriteRequestToNextNode(String key,String value){
        try {
            Socket socket=null;
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(myNode.getNextPortNum()));
            PrintWriter out =
                    new PrintWriter(socket.getOutputStream(), true);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out.println("WRITE_REQUEST" + "/" + key + "/" + value);
            String inputLine = in.readLine();
            if(inputLine.equals("WRITE_DONE")) {
                Log.i(TAG, "write forwared and done " + key + " to " + myNode.getNextPortNum());
            }
        }catch (Exception e) {
            Log.e(TAG + " provider", "File forward write request failed");
        }
    }

    public String forwardReadRequestToNextNode(String sourcePortNum,String key){
        Log.i(TAG,"forwarding read request to next node " + myNode.getNextPortNum());
        String result = "";
        try {
            Socket socket=null;
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(myNode.getNextPortNum()));
            PrintWriter out =
                    new PrintWriter(socket.getOutputStream(), true);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out.println("READ_REQUEST" + "/" + sourcePortNum + "/" + key);
            String inputLine = in.readLine();
            String[] resultSet = inputLine.split("/");
            if(resultSet[0].equals("READ_DONE")) {
                result = resultSet[1];
            }

        }catch (Exception e) {
            Log.e(TAG + " provider", "file read forware request failed");
        }
        return result;
    }

    public String doLocalRead(String key){
        Log.i(TAG,"doing local read " + myNode.getPortNum() + " " + key);
        String result = "";
        try {
            FileInputStream fis = getContext().openFileInput(key);
            InputStreamReader inputStreamReader =
                    new InputStreamReader(fis, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String value = reader.readLine();
            result = value;
        }catch (Exception e){
            Log.e(TAG,"file read forward request failed" + e.toString());
        }
        return result;
    }

    public void doLocalDeleteOfAllRecords(){
        String[] fileNames = getContext().fileList();
        for (String file : fileNames) {
            getContext().deleteFile(file);
        }
    }

    public void forwardDeleteAllRequestToNextNode(String sourcePortNum){
        Log.i(TAG,"forwarding delete all request to next node " + myNode.getNextPortNum());
        String result = "";
        try {
            Socket socket=null;
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(myNode.getNextPortNum()));
            PrintWriter out =
                    new PrintWriter(socket.getOutputStream(), true);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out.println("DELETE_ALL_REQUEST" + "/" + sourcePortNum);
            String inputLine = in.readLine();
            if(inputLine.equals("DELETE_ALL_DONE")) {
                return;
            }

        }catch (Exception e) {
            Log.e(TAG + " provider", "file read forware request failed");
        }
        return;
    }

    public void forwardDeleteRequestToNextNode(String sourcePortNum,String key){
        Log.i(TAG,"forwarding delete request to next node " + myNode.getNextPortNum());
        String result = "";
        try {
            Socket socket=null;
            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(myNode.getNextPortNum()));
            PrintWriter out =
                    new PrintWriter(socket.getOutputStream(), true);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out.println("DELETE_REQUEST" + "/" + sourcePortNum + "/" + key);
            String inputLine = in.readLine();
            if(inputLine.equals("DELETE_DONE")) {
                return;
            }

        }catch (Exception e) {
            Log.e(TAG + " provider", "file read forware request failed");
        }
        return;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if(myNode.prevId == null && myNode.nextId == null){
            Log.i(TAG,"query string : " + selection);
            if(selection.equals("*")||selection.equals("@")){
                doLocalDeleteOfAllRecords();
            } else{
                getContext().deleteFile(selection);
            }
        }else{

            if(selection.equals("@")){
                doLocalDeleteOfAllRecords();
            }else if(selection.equals("*")){
                doLocalDeleteOfAllRecords();
                forwardDeleteAllRequestToNextNode(myNode.getPortNum());
            }else {
                try{
                    if(checkLocalPossiblity(genHash(selection))){
                        getContext().deleteFile(selection);
                    }else{
                        forwardDeleteRequestToNextNode(myNode.getPortNum(),selection);
                    }
                }catch (Exception e){
                    Log.e(TAG,"error deleting key at " + myNode.getPortNum());
                }


            }
        }

        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String filename = (String) values.get("key");
        String value = (String) values.get("value");


        if(myNode.prevId == null && myNode.nextId == null){
            localWrite(filename,value);
            Log.v("insert", values.toString());
            return uri;
        }else{
            try{
                String hashedKey = genHash(filename);
                if(checkLocalPossiblity(hashedKey)){
                    localWrite(filename,value);
                }else{
                    // forward request
                    forwardWriteRequestToNextNode(filename,value);
                }

            }catch (Exception e){
                Log.e(TAG + " provider", "File forward write request");
            }

        }
        return null;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }catch(IOException e){
            Log.e(TAG, "Can't create a ServerSocket");
        }
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"JOIN_REQUEST",myPort);
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub

        String[] ColumnNames = new String[]{"key","value"};
        MatrixCursor cr = new MatrixCursor(ColumnNames,2);


        Log.i(TAG,"query string : " + selection);
        if(selection.equals("*")){
            String result = "";
            String nextNodeResult = "";
            String localResult = "";
            if(myNode.getNextId() !=null){
                nextNodeResult += getRecordsFromSuccessiveNodes(myNode.getPortNum());
            }
            localResult += getAllLocalRecords();
            if(localResult.equals("")){
                result = nextNodeResult;
            }else if(nextNodeResult.equals("")){
                result = localResult;
            }else{
                result = localResult + "/" + nextNodeResult;
            }
            String[] resultSplit = result.split("/");
            int count = 0;
            for(String record:resultSplit){
                count++;

                String[] keyValuePair = record.split("-");
                Log.i(TAG,"record key value pair"  + record);
                if(keyValuePair.length>1){
                    cr.newRow()
                            .add("key", keyValuePair[0])
                            .add("value", keyValuePair[1]);
                }
            }
            Log.i(TAG,"Total Records count : " + count);
            return cr;
        }else if(selection.equals("@")){
            for(String file : getContext().fileList()){
                try{
                    FileInputStream fis = getContext().openFileInput(file);
                    InputStreamReader inputStreamReader =
                            new InputStreamReader(fis, StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(inputStreamReader);
                    String value = reader.readLine();
                    Log.i(TAG,"read key: " + file);
                    cr.newRow()
                            .add("key", file)
                            .add("value", value);
                }catch (Exception e){
                    Log.e(TAG + " provider", e.getMessage());
                }
            }
            return cr;
        }else{
            try{
                Log.i(TAG,"particular key read");
                String result = "";
                String hashedKey = genHash(selection);
                if(myNode.getNextId()==null && myNode.getPrevId() == null){
                    result = doLocalRead(selection);
                }else{
                    if(checkLocalPossiblity(hashedKey)){
                        result = doLocalRead(selection);
                    }else{
                        result = forwardReadRequestToNextNode(myNode.getPortNum(),selection);
                    }
                }
                Log.i(TAG,"read key: " + selection);
                Log.i(TAG,result);
                cr.newRow()
                        .add("key", selection)
                        .add("value", result);
                return cr;
            }catch (Exception e){
                Log.e(TAG + " provider", e.toString());
            }
            return null;
        }
    }


    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        private void sendUpdatedOrder(Node curNode){
            try {
                Socket socket=null;
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(curNode.getPortNum()));
                PrintWriter out =
                        new PrintWriter(socket.getOutputStream(), true);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                out.println("ORDER_UPDATION" + "/" + curNode.getId() + "/" + curNode.getPortNum() + "/"
                        + curNode.getPrevId() + "/" + curNode.getPrevPortNum() + "/"
                        + curNode.getNextId() + "/" + curNode.getNextPortNum());
                String inputLine = in.readLine();
                if(inputLine.equals("ORDER_UPDATED")){
                    Log.i(TAG,"node order updated " + myPort);
                }
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
        }

        private void setPredecessorAndSuccessor(Node curNode){

            int curIdx = currentNodes.indexOf(curNode);
            if(currentNodes.size() == 1){
                curNode.setPrevId(null);
                curNode.setPrevPortNum(null);
                curNode.setNextId(null);
                curNode.setNextPortNum(null);
            }else if(currentNodes.size()==2){
                if(curIdx == 1){
                    curNode.setPrevId(currentNodes.get(0).getId());
                    curNode.setPrevPortNum(currentNodes.get(0).getPortNum());
                    curNode.setNextId(currentNodes.get(0).getId());
                    curNode.setNextPortNum(currentNodes.get(0).getPortNum());
                }else if(curIdx == 0){
                    curNode.setPrevId(currentNodes.get(1).getId());
                    curNode.setPrevPortNum(currentNodes.get(1).getPortNum());
                    curNode.setNextId(currentNodes.get(1).getId());
                    curNode.setNextPortNum(currentNodes.get(1).getPortNum());
                }
            }else {
                if(curIdx == 0){
                    curNode.setPrevId(currentNodes.get(currentNodes.size()-1).getId());
                    curNode.setPrevPortNum(currentNodes.get(currentNodes.size()-1).getPortNum());
                    curNode.setNextId(currentNodes.get(curIdx+1).getId());
                    curNode.setNextPortNum(currentNodes.get(curIdx+1).getPortNum());
                }else if(curIdx == currentNodes.size()-1){
                    curNode.setPrevId(currentNodes.get(curIdx-1).getId());
                    curNode.setPrevPortNum(currentNodes.get(curIdx-1).getPortNum());
                    curNode.setNextId(currentNodes.get(0).getId());
                    curNode.setNextPortNum(currentNodes.get(0).getPortNum());
                }else{
                    curNode.setPrevId(currentNodes.get(curIdx-1).getId());
                    curNode.setPrevPortNum(currentNodes.get(curIdx-1).getPortNum());
                    curNode.setNextId(currentNodes.get(curIdx+1).getId());
                    curNode.setNextPortNum(currentNodes.get(curIdx+1).getPortNum());
                }
            }
        }

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            try{
                while(true) {
                    Socket clientSocket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out =
                            new PrintWriter(clientSocket.getOutputStream(), true);
                    String receivedMsg = in.readLine();
                    String[] config = receivedMsg.split("/");
                    if(config[0].equals("JOIN_REQUEST")){
                        Node node = new Node();
                        node.setId(genHash(config[2]));
                        node.setPortNum(config[1]);
                        currentNodes.add(node);
                        Collections.sort(currentNodes,new NodeComparator());
                        Log.i(TAG,"currentNodes sorted");
                        for(Node curNode:currentNodes){

                            setPredecessorAndSuccessor(curNode);

                            if(curNode.getPortNum().equals(myPort)){
                                Log.i(TAG,"ORDER_UPDATION" + "/" + curNode.getId() + "/order port num : " + curNode.getPortNum() + "/"
                                        + curNode.getPrevId() + "/ previous port num" + curNode.getPrevPortNum() + "/"
                                        + curNode.getNextId() + "/ next port num" + curNode.getNextPortNum());
                                myNode = curNode;
                            }else{
                                Log.i(TAG,"ORDER_UPDATION" + "/" + curNode.getId() + "/order port num : " + curNode.getPortNum() + "/"
                                        + curNode.getPrevId() + "/ previous port num" + curNode.getPrevPortNum() + "/"
                                        + curNode.getNextId() + "/ next port num" + curNode.getNextPortNum());
                                sendUpdatedOrder(curNode);
                            }
                        }
                        out.println("NODE_JOINED");
                    }else if(config[0].equals("ORDER_UPDATION")){
                        Log.i(TAG,"ORDER UPDATION");
                        Node node = new Node();
                        node.setId(config[1]);
                        node.setPortNum(config[2]);
                        node.setPrevId(config[3]);
                        node.setPrevPortNum(config[4]);
                        node.setNextId(config[5]);
                        node.setNextPortNum(config[6]);
                        myNode = node;
                        out.println("ORDER_UPDATED");
                    }else if(config[0].equals("WRITE_REQUEST")){
                        try{
                            String hashedKey = genHash(config[1]);
                            if(checkLocalPossiblity(hashedKey)){
                                localWrite(config[1],config[2]);
                            }else {
                                // forward request
                                forwardWriteRequestToNextNode(config[1],config[2]);
                            }
                            out.println("WRITE_DONE");

                        }catch (Exception e){
                            Log.e(TAG + " provider", "File forward write request");
                        }
                    }else if(config[0].equals("REQUEST_ALL_VALUES")){
                        String result = "";
                        String nextNodeResult = "";
                        String localResult = "";
                        if(!myNode.getNextPortNum().equals(config[1])){
                            nextNodeResult += getRecordsFromSuccessiveNodes(config[1]);
                        }
                        String[] nextNodeResultSet = nextNodeResult.split("/");
                        localResult += getAllLocalRecords();
                        if(localResult.equals("")){
                           result = nextNodeResult;
                        }else if(nextNodeResult.equals("")){
                           result = localResult;
                        }else{
                            result = localResult + "/" + nextNodeResult;
                        }

                        String[] totalResult = result.split("/");
                        Log.i(TAG,"next node result " + nextNodeResult);
                        Log.i(TAG,"total result after receiving " + nextNodeResultSet.length);
                        Log.i(TAG,"current node result " + localResult);
                        Log.i(TAG,"total result after receiving and local " + totalResult.length);
                        out.println(result);
                    }else if(config[0].equals("READ_REQUEST")){
                        String result = "";
                        if(!checkLocalPossiblity(genHash(config[2]))){
                            if(!myNode.getNextPortNum().equals(config[1])){
                                result += "READ_DONE" + "/" + forwardReadRequestToNextNode(config[1],config[2]);
                            }
                        }else{
                            result += "READ_DONE" + "/" + doLocalRead(config[2]);
                        }

                        out.println(result);
                    }else if(config[0].equals("DELETE_ALL_REQUEST")){
                        doLocalDeleteOfAllRecords();
                        if(!myNode.getNextPortNum().equals(config[1])){
                            forwardDeleteAllRequestToNextNode(config[1]);
                        }
                        out.println("DELETE_ALL_DONE");
                    }else if(config[0].equals("DELETE_REQUEST")){
                        if(!checkLocalPossiblity(genHash(config[2]))){
                            if(!myNode.getNextPortNum().equals(config[1])){
                                forwardDeleteRequestToNextNode(config[1],config[2]);
                            }
                        }else{
                            getContext().deleteFile(config[2]);
                        }
                        out.println("DELETE_DONE");
                    }

                }
            }catch(Exception e) {
                System.out.println(e.toString());
            }
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            return null;
        }

        private Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();


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

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                Socket socket=null;
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(MainPort));
                PrintWriter out =
                        new PrintWriter(socket.getOutputStream(), true);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                out.println("JOIN_REQUEST" + "/" + myPort + "/" + String.valueOf(Integer.parseInt(myPort)/2));
                String inputLine = in.readLine();
                if(inputLine.equals("NODE_JOINED")){
                    Log.i(TAG,"node joined " + myPort);
                }
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */

            }catch (NullPointerException e) {
                Log.i(TAG,"node joined " + myPort);
                MainPort = myPort;
                pingItself();
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }

        private void pingItself(){
            try {
                Socket socket=null;
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(MainPort));
                PrintWriter out =
                        new PrintWriter(socket.getOutputStream(), true);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                out.println("JOIN_REQUEST" + "/" + myPort + "/" + String.valueOf(Integer.parseInt(myPort)/2));
                String inputLine = in.readLine();
                if(inputLine.equals("NODE_JOINED")){
                    Log.i(TAG,"node joined " + myPort);
                }
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
        }
    }
}

class Node{
    String id;
    String portNum;
    String prevId;
    String nextId;
    String prevPortNum;
    String nextPortNum;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrevId() {
        return prevId;
    }

    public void setPrevId(String prevId) {
        this.prevId = prevId;
    }

    public String getNextId() {
        return nextId;
    }

    public String getPortNum() {
        return portNum;
    }

    public void setPortNum(String portNum) {
        this.portNum = portNum;
    }

    public void setNextId(String nextId) {
        this.nextId = nextId;
    }

    public String getPrevPortNum() {
        return prevPortNum;
    }

    public void setPrevPortNum(String prevPortNum) {
        this.prevPortNum = prevPortNum;
    }

    public String getNextPortNum() {
        return nextPortNum;
    }

    public void setNextPortNum(String nextPortNum) {
        this.nextPortNum = nextPortNum;
    }
}