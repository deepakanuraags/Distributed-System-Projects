package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;

 public class SimpleDynamoProvider extends ContentProvider {

	class Neighbor{
		public String getMyPort() {
			return myPort;
		}

		public void setMyPort(String myPort) {
			this.myPort = myPort;
		}

		String myPort;
		String nextPort;
		String nextPortHashed;
		String nextPlusPort;
		String nextPlusPortHashed;
		String prevPlusPort;

		public String getPrevPlusPort() {
			return prevPlusPort;
		}

		public void setPrevPlusPort(String prevPlusPort) {
			this.prevPlusPort = prevPlusPort;
		}

		public String getPrevPlusPortHashed() {
			return prevPlusPortHashed;
		}

		public void setPrevPlusPortHashed(String prevPlusPortHashed) {
			this.prevPlusPortHashed = prevPlusPortHashed;
		}

		String prevPlusPortHashed;

		public String getNextPortHashed() {
			return nextPortHashed;
		}

		public void setNextPortHashed(String nextPortHashed) {
			this.nextPortHashed = nextPortHashed;
		}

		public String getNextPlusPortHashed() {
			return nextPlusPortHashed;
		}

		public void setNextPlusPortHashed(String nextPlusPortHashed) {
			this.nextPlusPortHashed = nextPlusPortHashed;
		}

		public String getNextPort() {
			return nextPort;
		}

		public void setNextPort(String nextPort) {
			this.nextPort = nextPort;
		}

		public String getNextPlusPort() {
			return nextPlusPort;
		}

		public void setNextPlusPort(String nextPlusPort) {
			this.nextPlusPort = nextPlusPort;
		}


	}

	static final String TAG = SimpleDynamoActivity.class.getSimpleName();
	static String[] emulators = {"5554","5556","5558","5560","5562"};
	static Map<String,String> hashPortMap = new HashMap<String,String>();
	static Node myNode = new Node();
	static List<String> ring = new ArrayList<String>();
	static final int SERVER_PORT = 10000;
	static Map<String,String> copyState = new HashMap<String, String>();
	static Set<String> curFiles = new HashSet<String>();
	static Map<String,Neighbor> neighborMap = new HashMap<String,Neighbor>();
	static boolean min = false;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub

		if(selection.equals("*")){
			doLocalDeleteOfAllRecords();
			copyState = null;
		}else if(selection.equals("@")){
			doLocalDeleteOfAllRecords();
			copyState = null;
		}else {
			try{
				String hashKey = genHash(selection);
				int idx = getKeyIdx(hashKey);
				RunState curState1 = new RunState();
				curState1.setMyPort(hashPortMap.get(ring.get(idx)));
				curState1.setKey(selection);
				curState1.setCurrentState("delete");
				RunState curState2 = RunState.getStateClone(curState1);
				curState2.setMyPort(neighborMap.get(ring.get(idx)).nextPort);
				RunState curState3 = RunState.getStateClone(curState2);
				curState3.setMyPort(neighborMap.get(ring.get(idx)).nextPlusPort);
				Log.i(TAG,"neigbors for " + hashPortMap.get(ring.get(idx))  + " next port " + neighborMap.get(ring.get(idx)).nextPort + neighborMap.get(ring.get(idx)).nextPlusPort );
				Log.i(TAG,"delete for key " + selection + " has myPort at " + curState1.getMyPort() + " nextPort At" + curState2.getMyPort() + "nextPlusPort at " + curState3.getMyPort());
				TaskThread th1 = new TaskThread(curState1);
				TaskThread th2 = new TaskThread(curState2);
				TaskThread th3 = new TaskThread(curState3);
				th1.start();
				th2.start();
				th3.start();
			}
			catch (Exception e){

			}

		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}


	 public void doLocalDeleteOfAllRecords(){
		 String[] fileNames = getContext().fileList();
		 for (String file : fileNames) {
			 getContext().deleteFile(file);
		 }
	 }

	 public int getKeyIdx(String hashKey)
	 {
		 for(int idx=0;idx<ring.size();idx++)
		 {
			 int val1=hashKey.compareTo(ring.get(idx));

			 if(idx==0)
			 {
				 int val2=hashKey.compareTo(ring.get(ring.size()-1));
				 if(val1<=0 || val2>0)
				 {
					 return idx;
				 }
			 }
			 else
			 {
				 int val2=hashKey.compareTo(ring.get(idx-1));
				 if(val1<=0 && val2>0)
				 {
					 return idx;
				 }
			 }
		 }
		 return 0;
	 }

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		String status = (String) values.get("status");
		String key = (String) values.get("key");
		String value = (String) values.get("value");

		try{
			String hashKey = genHash(key);
			int i = getKeyIdx(hashKey);
			RunState curState1 = new RunState();
			curState1.setMyPort(hashPortMap.get(ring.get(i)));
			curState1.setKey(key);
			curState1.setValue(value);
			curState1.setCurrentState("firstInsertion");

			RunState curState2 = new RunState();
			curState2.setMyPort(hashPortMap.get(ring.get(i)));
			curState2.setKey(key);
			curState2.setValue(value);
			curState2.setCurrentState("copy");
			curState2.setNextPort(neighborMap.get(ring.get(i)).nextPort);
			curState2.setNextPlusPort(neighborMap.get(ring.get(i)).nextPlusPort);
			curState2.setMyPort(neighborMap.get(ring.get(i)).myPort);
			String mesge = 	curState2.getKey() + " " +curState2.getMyPort() + " " + curState2.getNextPort() + " " + curState2.getNextPlusPort();
			Log.i(TAG,"Ports found for key "+ mesge);
			TaskThread th1 = new TaskThread(curState1);
						TaskThread th2 = new TaskThread(curState2);
						th1.start();
						th2.start();


		}

		catch(Exception e){

		}
		return null;
	}

	public void localWrite(String filename,String value,String isNotMyFiles){
		try {
			FileOutputStream outputStream;
//			Log.i(TAG,"local writing key :" + filename);
			outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
			outputStream.write(value.getBytes());
			outputStream.close();
			if(isNotMyFiles==null){
				curFiles.add(filename);
			}
		} catch (Exception e) {
			Log.e(TAG + " provider", "File write failed insert");
		}
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub

		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String emulatorId = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myNode.setMyEmulatorId(emulatorId);
		myNode.setMyPort(String.valueOf((Integer.parseInt(emulatorId) * 2)));
		ReceiverThread receiver= new ReceiverThread();
		receiver.start();
		try{
			myNode.setMyPortHashed(genHash(emulatorId));
		}catch (Exception e){
			Log.e(TAG,"Exception thrown gen hash");
		}
		for(String emulator:emulators){
			try{
				String hashVal = genHash(emulator);
				hashPortMap.put(hashVal,String.valueOf((Integer.parseInt(emulator) * 2)));
				ring.add(hashVal);
			}catch(Exception e){
				Log.e(TAG,"Exception thrown gen hash");
			}

		}
		Collections.sort(ring);
		Log.i(TAG,"ring formed see the order below");
		for(String hash:ring){
			Log.i(TAG,hashPortMap.get(hash));
		}

		for(int i=0;i<ring.size();i++){
			if(myNode.getMyPortHashed().equals(ring.get(i))){
				if(i==0){
					myNode.setPrevPlusPort(hashPortMap.get(ring.get(ring.size()-2)));
					myNode.setPrevPlusPortHashed(ring.get(ring.size()-2));
					myNode.setPrevPort(hashPortMap.get(ring.get(ring.size()-1)));
					myNode.setPrevPortHashed(ring.get(ring.size()-1));
					myNode.setNextPort(hashPortMap.get(ring.get(i+1)));
					myNode.setNextPortHashed(ring.get(i+1));
					myNode.setNextPlusPort(hashPortMap.get(ring.get(i+2)));
					myNode.setNextPlusPortHashed(ring.get(i+2));
					Log.i(TAG,"Ports for my port are " + myNode.getPrevPlusPort() + " " + myNode.getPrevPort() + " " + myNode.getNextPort() + " " + myNode.getNextPlusPort());
				}else if(i==1){
					myNode.setPrevPlusPort(hashPortMap.get(ring.get(ring.size()-1)));
					myNode.setPrevPlusPortHashed(ring.get(ring.size()-1));
					myNode.setPrevPort(hashPortMap.get(ring.get(i-1)));
					myNode.setPrevPortHashed(ring.get(i-1));
					myNode.setNextPort(hashPortMap.get(ring.get(i+1)));
					myNode.setNextPortHashed(ring.get(i+1));
					myNode.setNextPlusPort(hashPortMap.get(ring.get(i+2)));
					myNode.setNextPlusPortHashed(ring.get(i+2));
					Log.i(TAG,"Ports for my port are " + myNode.getPrevPlusPort() + " " + myNode.getPrevPort() + " " + myNode.getNextPort() + " " + myNode.getNextPlusPort());
				}else if(i==ring.size()-1){
					myNode.setPrevPlusPort(hashPortMap.get(ring.get(i-2)));
					myNode.setPrevPlusPortHashed(ring.get(i-2));
					myNode.setPrevPort(hashPortMap.get(ring.get(i-1)));
					myNode.setPrevPortHashed(ring.get(i-1));
					myNode.setNextPort(hashPortMap.get((ring.get(0))));
					myNode.setNextPortHashed(ring.get(0));
					myNode.setNextPlusPort(hashPortMap.get(ring.get(1)));
					myNode.setNextPlusPortHashed(ring.get(1));
					Log.i(TAG,"Ports for my port are " + myNode.getPrevPlusPort() + " " + myNode.getPrevPort() + " " + myNode.getNextPort() + " " + myNode.getNextPlusPort());
				}else if(i==ring.size()-2){
					myNode.setPrevPlusPort(hashPortMap.get(ring.get(i-2)));
					myNode.setPrevPlusPortHashed(ring.get(i-2));
					myNode.setPrevPort(hashPortMap.get(ring.get(i-1)));
					myNode.setPrevPortHashed(ring.get(i-1));
					myNode.setNextPort(hashPortMap.get(ring.get(i+1)));
					myNode.setNextPortHashed(ring.get(i+1));
					myNode.setNextPlusPort(hashPortMap.get(ring.get(0)));
					myNode.setNextPlusPortHashed(ring.get(0));
					Log.i(TAG,"Ports for my port are " + myNode.getPrevPlusPort() + " " + myNode.getPrevPort() + " " + myNode.getNextPort() + " " + myNode.getNextPlusPort());
				}else {
					myNode.setPrevPlusPort(hashPortMap.get(ring.get(i-2)));
					myNode.setPrevPlusPortHashed(ring.get(i-2));
					myNode.setPrevPort(hashPortMap.get(ring.get(i-1)));
					myNode.setPrevPortHashed(ring.get(i-1));
					myNode.setNextPort(hashPortMap.get(ring.get(i+1)));
					myNode.setNextPortHashed(ring.get(i+1));
					myNode.setNextPlusPort(hashPortMap.get(ring.get(i+2)));
					myNode.setNextPlusPortHashed(ring.get(i+2));
					Log.i(TAG,"Ports for my port are " + myNode.getPrevPlusPort() + " " + myNode.getPrevPort() + " " + myNode.getNextPort() + " " + myNode.getNextPlusPort());
				}
			}

			if(i==0){
				Neighbor n = new Neighbor();
				n.setMyPort(hashPortMap.get(ring.get(i)));
				n.setNextPort(hashPortMap.get(ring.get(i+1)));
				n.setNextPlusPort(hashPortMap.get(ring.get(i+2)));
				neighborMap.put(ring.get(i),n);
			}else if(i==ring.size()-1){
				Neighbor n = new Neighbor();
				n.setMyPort(hashPortMap.get(ring.get(i)));
				n.setNextPort(hashPortMap.get(ring.get(0)));
				n.setNextPlusPort(hashPortMap.get(ring.get(1)));
				neighborMap.put(ring.get(i),n);
			}else if(i==ring.size()-2){
				Neighbor n = new Neighbor();
				n.setMyPort(hashPortMap.get(ring.get(i)));
				n.setNextPort(hashPortMap.get(ring.get(i+1)));
				n.setNextPlusPort(hashPortMap.get(ring.get(0)));
				neighborMap.put(ring.get(i),n);
			}else {
				Neighbor n = new Neighbor();
				n.setMyPort(hashPortMap.get(ring.get(i)));
				n.setNextPort(hashPortMap.get(ring.get(i+1)));
				n.setNextPlusPort(hashPortMap.get(ring.get(i+2)));
				neighborMap.put(ring.get(i),n);
			}
		}
		for(Map.Entry<String,Neighbor> neighbour : neighborMap.entrySet()){
			Log.i(TAG + "loading","neigbors for " + hashPortMap.get(neighbour.getKey())  + " next port " + neighbour.getValue().getNextPort() + neighbour.getValue().getNextPlusPort() );
		}
		RunState run = new RunState(myNode);
		run.currentState = "loadData";
		TaskThread th = new TaskThread(run);
		th.start();
		return false;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		// TODO Auto-generated method stub
		String[] ColumnNames = new String[]{"key","value"};
		MatrixCursor cr = new MatrixCursor(ColumnNames,2);
		if (selection.equals("*")) {

			for(String portHash:ring){
				RunState curState1 = new RunState();
				curState1.setMyPort(hashPortMap.get(portHash));
				curState1.setCurrentState("queryAll");
				TaskThread th = new TaskThread(curState1);
				th.start();
				String result = th.getAll();
				String[] resultSplit = result.split("/");
				int count = 0;
				for(String record:resultSplit){
					count++;
					String[] keyValuePair = record.split("-");
//					Log.i(TAG,"record key value pair "  + record);
					if(keyValuePair.length>1){
						cr.newRow()
								.add("key", keyValuePair[0])
								.add("value", keyValuePair[1]);
					}
				}

			}
			return cr;
		}else if(selection.equals("@")) {
			String result = getAllLocalRecords();
			String[] resultSplit = result.split("/");
			int count = 0;
			for (String record : resultSplit) {
				count++;
				String[] keyValuePair = record.split("-");
//				Log.i(TAG, "record key value pair" + record);
				if (keyValuePair.length > 1) {
					cr.newRow()
							.add("key", keyValuePair[0])
							.add("value", keyValuePair[1]);
				}
			}
			return cr;
		}
		else {
				String hashKey = "";
				try{
					hashKey = genHash(selection);
				}catch (Exception e){
					Log.e(TAG,"error while generating hash");
				}
			    int idx = getKeyIdx(hashKey);
				Log.i(TAG,"querying for " + selection + " at " + myNode.getMyPort());

				RunState curState1 = new RunState();
					curState1.setMyPort(hashPortMap.get(ring.get(idx)));
					curState1.setKey(selection);
					curState1.setCurrentState("query");
					TaskThread th1 = new TaskThread(curState1);
					Log.i(TAG,"querying for " + selection + " found node at " + curState1.getMyPort());
					th1.start();
					String result = th1.get();
					if(result!=null){
						cr.newRow()
								.add("key", selection)
								.add("value", result);
						return cr;
					}else {
						RunState curState2 = new RunState();
						Log.i(TAG, "querying next port for " + selection +  " found node at " +neighborMap.get(ring.get(idx)).nextPort);
						curState2.setMyPort(neighborMap.get(ring.get(idx)).nextPort);
						curState2.setKey(selection);
						curState2.setCurrentState("query");
						TaskThread th2 = new TaskThread(curState2);
						th2.start();
						String result2 = th2.get();
						cr.newRow()
								.add("key", selection)
								.add("value", result2);
						return cr;
					}

		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
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

	private String getAllLocalRecords(){
//		Log.i(TAG,"getting all local records" + " " + myNode.getMyPort());
		String result = "";
		int count = 0;
		for(String file : getContext().fileList()){
//			Log.i(TAG,"getting local record for all local records" + file);
			count++;
			try{
				FileInputStream fis = getContext().openFileInput(file);
				InputStreamReader inputStreamReader =
						new InputStreamReader(fis, StandardCharsets.UTF_8);
				BufferedReader reader = new BufferedReader(inputStreamReader);
				String value = reader.readLine();
//				Log.i(TAG,"read key: " + file);
				if(result.equals("")){
					result += file + "-" + value;
				}else{
					result += "/" + file + "-" + value;
				}
//				Log.i(TAG,"Total Read Count at Local : " + count + " at port " + myNode.getMyPort());

			}catch (Exception e){
				Log.e(TAG + " provider", e.getMessage());
			}
		}
		return result;
	}

	public String doLocalRead(String key){
//		Log.i(TAG,"doing local read " + myNode.getMyPort() + " " + key);
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

	class ReceiverThread extends Thread {

		public void run() {
			try {
				ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
				while (true) {
					Socket clientSocket = serverSocket.accept();
					BufferedReader in = new BufferedReader(
							new InputStreamReader(clientSocket.getInputStream()));
					PrintWriter out =
							new PrintWriter(clientSocket.getOutputStream(), true);
					String receivedMsg = in.readLine();
					String[] config = receivedMsg.split("/");
					Log.i(TAG,"Connection received for " + config[0]);
					if(config[0].equals("copy")){
						localWrite(config[1],config[2],"not my files");
						out.println("copy done");
						updateCopyStatus(config);
					}else if(config[0].equals("firstInsertion")){
						localWrite(config[1],config[2],null);
						out.println("insertion done");
					}else if(config[0].equals("queryAll")){
						String result = getAllLocalRecords();
						out.println(result);;
					}else if(config[0].equals("query")){
						String result = doLocalRead(config[1]);
						out.println(result);
					}else if(config[0].equals("delete")){
						Log.i(TAG,"trying to delete at " + myNode.getMyPort() + " for key " + config[1]);
						getContext().deleteFile(config[1]);
						if(copyState.containsKey(config[1])){
							copyState.remove(config[1]);
						}
						out.println("delete done");
					}else if(config[0].equals("loadData")){
						Log.i(TAG + "loading","load connection received for loadData");
						Log.i(TAG + "loading" , "load connection received for loadData from port " + config[2] + " and request " + config[1]);
						if(copyState!=null){
							if(config[1].equals("nextPort")){
								Log.i(TAG + "loading","next port");
								String prevVals = "";
								String prevPrevVals = "";
								for (Map.Entry<String, String> cpState : copyState.entrySet()) {
								Log.i(TAG + "loading","load copy state vals are , " + " nextPort " + cpState.getKey() + cpState.getValue());
									String valState[]=cpState.getValue().split("~");

									if(valState[0].equals("penultimate")){
										prevVals = prevVals + cpState.getKey() + "-" + valState[1] + "/";
									}else if(valState[0].equals("antePenultimateKey")){
										prevPrevVals = prevPrevVals + cpState.getKey() + "-" + valState[1] + "/";
									}
								}
								if(prevVals.equals("")){
									prevVals = "empty";
								}
								if(prevPrevVals.equals("")){
									prevPrevVals = "empty";
								}
								String totalResult = prevVals + "~~" + prevPrevVals;
								Log.i(TAG+"loading", "final outing result " + totalResult );
								out.println(totalResult);
							}else if(config[1].equals("prevPort")){
								Log.i(TAG + "loading","prev port");
								String prevVals = "";
								for (Map.Entry<String, String> cpState : copyState.entrySet()) {
									Log.i(TAG + "loading","load copy state vals are , " + " prevPort " + cpState.getKey() + cpState.getValue());
									String valState[]=cpState.getValue().split("~");

									if(valState[0].equals("penultimate")){
										prevVals = prevVals + cpState.getKey() + "-" + valState[1] + "/";
									}
								}
								Log.i(TAG+"loading", "final outing result " + prevVals );
								out.println(prevVals);
							}
						}
					}else if(config[0].equals("loadDataOnCrash")){
						Log.i(TAG+ "loading","connection received for loadDataOnCrash");
						Log.i(TAG + "loading" , "connection received for loadDataOnCrash from port " + config[2] + " and request " + config[1]);
							if(config[1].equals("nextPlusPort")){
								Log.i(TAG + "loading","next plus port");
								String prevPrevVals = "";
								for (Map.Entry<String, String> cpState : copyState.entrySet()) {
									Log.i(TAG + "loading","load copy state vals are , " + " nextPlusport " + cpState.getKey() + cpState.getValue());
									String valState[]=cpState.getValue().split("~");
									if(valState[0].equals("antePenultimateKey")){
										prevPrevVals = prevPrevVals + cpState.getKey() + "-" + valState[1] + "/";
									}
								}
								Log.i(TAG+"loading", "final outing result " + prevPrevVals );
								out.println(prevPrevVals);
							}else if(config[1].equals("prevPort")){
								Log.i(TAG+ "loading","prev port");
								String prevVals="";
								String localVals= "";
								for (Map.Entry<String, String> cpState : copyState.entrySet()) {
									Log.i(TAG+ "loading","load copy state vals are , " + " prevPort " + cpState.getKey() + cpState.getValue());
									String valState[]=cpState.getValue().split("~");

									if(valState[0].equals("penultimate")){
										prevVals = prevVals + cpState.getKey() + "-" + valState[1] + "/";
									}
								}
								for(String file:curFiles){
									String result = file + "-" + doLocalRead(file);
									Log.i(TAG+ "loading","load curFiles vals are , " + " prevPort " + file + " "+  doLocalRead(file));
									localVals = localVals + result + "/";
								}
								if(localVals.equals("")){
									localVals = "empty";
								}
								if(prevVals.equals("")){
									prevVals = "empty";
								}
								String totalResult = localVals + "~~" + prevVals;
								Log.i(TAG+"loading", "final outing result " + totalResult);
								out.println(totalResult);
							}else if(config[1].equals("prevPlusPort")){
								Log.i(TAG+ "loading", "prev plus port");
								String localVals= "";
								for(String file:curFiles){
									Log.i(TAG+ "loading","load curFiles vals are , " + " prevPlusPort " + file + " "+  doLocalRead(file));
									String result = file + "-" + doLocalRead(file);
									localVals = localVals + result + "/";
								}
								Log.i(TAG+"loading", "final outing result " + localVals);
								out.println(localVals);
							}
						}
					}
			}catch (Exception e){
						Log.i(TAG,"error at server " + e.toString());
			}

		}



		void updateCopyStatus(String[] config){
			if(myNode.getPrevPort().equals(config[3])){
				Log.i(TAG,"loading writing penultimate key " + config[1] + "~" + config[2]);
				copyState.put(config[1],"penultimate" + "~" + config[2]);
			}else{
				Log.i(TAG,"loading writing antePenultimate key " + config[1] + "~" + config[2]);
				copyState.put(config[1],"antePenultimateKey" + "~" + config[2]);
			}
		}
	}

	class TaskThread extends Thread {

		RunState curState;

		TaskThread(RunState curState) {
			this.curState = curState;
		}

		public void run() {
			try {
				if (curState.getCurrentState().equals("copy")) {

					String message = "copy/" + curState.getKey() + "/" + curState.getValue() + "/" + curState.getMyPort() + "/" + curState.getMyPortHashed();
					try {
						String response1 = pingServer(curState.getNextPort(), message);
						if (response1.equals("copy done")) {
							Log.i(TAG, "replication done at " + curState.getNextPort() + " for " + curState.getKey());
						}

					} catch (Exception e) {
						Log.e(TAG, e.toString() + "----" + "copy1");
					}
					try {
						String response2 = pingServer(curState.getNextPlusPort(), message);
						if (response2.equals("copy done")) {
							Log.i(TAG, "replication done at " + curState.getNextPlusPort() + " for " + curState.getKey());
						}
					} catch (Exception e) {
						Log.e(TAG, e.toString() + "----" + "copy2");
					}

				} else if (curState.getCurrentState().equals("firstInsertion")) {
					String message = "firstInsertion/" + curState.getKey() + "/" + curState.getValue();
					try {
						String response1 = pingServer(curState.getMyPort(), message);
						if (response1.equals("insertion done")) {
							Log.i(TAG, "insertion done at " + curState.getMyPort() + " for " + curState.getKey());
						}
					} catch (Exception e) {
						Log.e(TAG, e.toString() + "----" + "firstInsertion");
					}
				} else if (curState.getCurrentState().equals("delete")) {
					String message = "delete/" + curState.getKey();
					try {
						String response1 = pingServer(curState.getMyPort(), message);
						if (response1.equals("delete done")) {
							Log.i(TAG, "delete done at " + curState.getMyPort() + " for " + curState.getKey());
						}
					} catch (Exception e) {
						Log.e(TAG, e.toString() + "----" + "delete");
					}
				} else if (curState.getCurrentState().equals("loadData")) {
					Log.i(TAG, "Loading Data at " + curState.getMyPort());
					Log.i(TAG, "loading my next port " + curState.getNextPort());
					Log.i(TAG, "loading my next plus port " + curState.getNextPlusPort());
					Log.i(TAG, "loading my prev port " + curState.getPrevPort());
					Log.i(TAG, "loading my prev plus port " + curState.getPrevPlusPort());
					try {
						String message = "loadData/" + "nextPort/" + curState.getMyPort();
						Log.i(TAG, "loading ping next port " + curState.getNextPort());
						String response1 = pingServer(curState.getNextPort(), message);
						String[] results = response1.split("~~");
						Log.i(TAG, "results next port are " + response1);
						Log.i(TAG, "results length is " + results.length);
						String results1 = results[0];
						String results2 = results[1];
						if (!results1.equals("empty")) {
							String[] myReplicas = results1.split("/");
							Log.i(TAG, "loading values received from next port are my values");
							Log.i(TAG, "loading myValues " + results1);
							insertMultiLocal(myReplicas, "nonReplicas", "", "loading values received from next port are my values");
						}
						if (!results2.equals("empty")) {
							String[] prevPortToMeLocalVals = results2.split("/"); // replicate it
							Log.i(TAG, "loading values received from next port are my prev values");
							Log.i(TAG, "loading prevPortToMeLocalVals " + results2);
							insertMultiLocal(prevPortToMeLocalVals, "replicas", curState.getPrevPort(), "loading values received from next port are my prev values");

						}
					} catch (Exception e) {
						String message = "loadDataOnCrash/" + "nextPlusPort/" + curState.getMyPort();
						Log.i(TAG, "loading error on pinging next port so pinging next plus port" + curState.getNextPort());
						Log.i(TAG, "loading ping nextPlus port " + curState.getNextPlusPort());
						String response1 = pingServer(curState.getNextPlusPort(), message);
						if (response1 != null) {
							String[] myReplicas = response1.split("/");
							insertMultiLocal(myReplicas, "nonReplicas", "", "loading values received from next plus port");

							message = "loadDataOnCrash/" + "prevPort/" + curState.getMyPort();
							;
							String response2 = pingServer(curState.getPrevPort(), message);
							if (response2 != null) {
								Log.i(TAG, "results from prev port are " + response2);
								String[] results = response2.split("~~");
								Log.i(TAG, "results length is " + results.length);
								String results1 = results[0];
								String results2 = results[1];
								if (!results1.equals("empty")) {
									String[] prevPortToMeLocalVals = results1.split("/");
									insertMultiLocal(prevPortToMeLocalVals, "replicas", curState.getPrevPort(), "loading values received from prev port are its local vals and my replicas");
								}
								if (!results2.equals("empty")) {
									String[] prevPlusPortToMeLocalVals = results2.split("/");
									insertMultiLocal(prevPlusPortToMeLocalVals, "replicas", curState.getPrevPlusPort(), "loading values received from prev plus port are its local vals and my replicas");
								}
							}

						}
					}

					try {
						String message = "loadData/" + "prevPort/" + curState.getMyPort();
						Log.i(TAG, "loading ping prev port" + curState.getPrevPort());
						String response3 = pingServer(curState.getPrevPort(), message);
						String[] prevPlusPortToMeLocalVals = response3.split("/");
						Log.i(TAG, "loading values received from prev port are my prev plus values");
						Log.i(TAG, "loading prevPlusPortToMeLocalVals " + response3);
						insertMultiLocal(prevPlusPortToMeLocalVals, "replicas", curState.getPrevPlusPort(), "loading values received from prev port are my prev plus values");
					} catch (Exception e) {
						String message = "loadDataOnCrash/" + "prevPlusPort/" + curState.getMyPort();
						;
						Log.i(TAG, "loading error on pinging prev port so pinging prevPlus port" + curState.getPrevPort());
						String response3 = pingServer(curState.getPrevPlusPort(), message);
						if (response3 != null) {
							String[] prevPlusPortToMeLocalVals = response3.split("/");
							insertMultiLocal(prevPlusPortToMeLocalVals, "replicas", curState.getPrevPlusPort(), "loading values received from prev plus port are my replicas");
						}
					}
				}
			}catch (Exception e){
				Log.i(TAG + "Task Thread" ,"Exception at task thread" +  e.toString());
			}
		}

		// called while loading data
		private void insertMultiLocal(String[] results,String insertionState,String port,String message1){
			Log.i(TAG," current insertion state " + insertionState);
			Log.i(TAG+"loading", "at insert multilocal all results" +results);
			Log.i(TAG,message1);
			for(String tuple:results){
				try {
					Log.i(TAG+"loading", "at insert multilocal current tuple" +tuple);
					String[] keyValuePair = tuple.split("-");
					if (keyValuePair.length > 1) {
						Log.i(TAG,"exception" + keyValuePair[0] + " - " + keyValuePair[1] + " " + message1);
						String filename = keyValuePair[0];
						String value = keyValuePair[1];
						FileOutputStream outputStream;
						outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
						outputStream.write(value.getBytes());
						outputStream.close();

						if(insertionState.equals("replicas")){
							String message = filename + "/" + value + "/" + port;
							String[] config = message.split("/");
							updateCopyStatus(config);
						}else{
							curFiles.add(filename);
						}
					}
				} catch (Exception e) {
					Log.e(TAG + " provider", e.toString() + "error in insert at multi local");
				}
			}
		}

		// called while loading data
		void updateCopyStatus(String[] config){
			if(curState.getPrevPort().equals(config[2])){
				Log.i(TAG,"loading writing penultimate key " + config[0] + "~" + config[1]);
				copyState.put(config[0],"penultimate" + "~" + config[1]);
			}else{
				Log.i(TAG,"loading ante penultimate key " + config[0] + "~" + config[1]);
				copyState.put(config[0],"antePenultimateKey" + "~" + config[1]);
			}
		}

		public String getAll(){
			String message = "queryAll/" + curState.getMyPort();
			try {
				String response1 = pingServer(curState.getMyPort(), message);
//				Log.i(TAG, "query All done at " + curState.getMyPort());
				if(response1 == null){
					return "";
				}
				return response1;
			} catch (Exception e) {
				Log.e(TAG, "error while querying all");
			}
			return "";
		}

		public String get(){
			String message = "query/" + curState.getKey();
			try {
				String response1 = pingServer(curState.getMyPort(), message);
//				Log.i(TAG, "query All done at " + curState.getKey());
				return response1;
			} catch (Exception e) {
				Log.e(TAG, "error while querying");
			}
			return "";
		}


		String pingServer(String targetPort, String message) {
			Log.i(TAG,"Pinging Server " + targetPort);
			Socket socket;
			try {
				socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(targetPort));
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				out.println(message);
				String inputLine = in.readLine();
				socket.close();
				return inputLine;
			} catch (Exception e) {
				Log.e(TAG, "error while pinging server with target port " + targetPort + " " + message);
				return null;
			}
		}
	}
}





			class RunState extends Node {
				String key;
				String value;
				String currentState;

				public static RunState getStateClone(RunState state){
					RunState temp = new RunState();
					temp.setMyPort(state.getMyPort());
					temp.setMyPortHashed(state.getMyPortHashed());
					temp.setNextPort(state.getNextPort());
					temp.setNextPortHashed(state.getNextPortHashed());
					temp.setNextPlusPortHashed(state.getNextPlusPortHashed());
					temp.setNextPlusPort(state.getNextPlusPort());
					temp.setPrevPort(state.getPrevPort());
					temp.setPrevPortHashed(state.getPrevPortHashed());
					temp.setPrevPlusPort(state.getPrevPlusPort());
					temp.setPrevPlusPortHashed(state.getPrevPlusPortHashed());
					temp.setMyEmulatorId(state.getMyEmulatorId());
					temp.setKey(state.getKey());
					temp.setValue(state.getValue());
					temp.setCurrentState(state.getCurrentState());
					return temp;

				}

				public RunState(Node myNode) {
					super(myNode);
				}

				public RunState(){

				}

				public String getKey() {
					return key;
				}

				public void setKey(String key) {
					this.key = key;
				}

				public String getValue() {
					return value;
				}

				public void setValue(String value) {
					this.value = value;
				}

				public String getCurrentState() {
					return currentState;
				}

				public void setCurrentState(String currentState) {
					this.currentState = currentState;
				}

			}

			class Node {

				String myEmulatorId;
				String myPort;
				String myPortHashed;
				String nextPort;
				String nextPortHashed;
				String nextPlusPort;
				String nextPlusPortHashed;
				String prevPort;
				String prevPortHashed;
				String prevPlusPort;
				String prevPlusPortHashed;

				public Node(Node myNode) {
					this.myEmulatorId = myNode.getMyEmulatorId();
					this.myPort = myNode.getMyPort();
					this.myPortHashed = myNode.getMyPortHashed();
					this.nextPort = myNode.getNextPort();
					this.nextPortHashed = myNode.getNextPortHashed();
					this.nextPlusPort = myNode.getNextPlusPort();
					this.nextPlusPortHashed = myNode.getNextPlusPortHashed();
					this.prevPort = myNode.getPrevPort();
					this.prevPortHashed = myNode.getPrevPortHashed();
					this.prevPort = myNode.getPrevPort();
					this.prevPlusPort = myNode.getPrevPlusPort();
				}



				public Node(){

				}

				public String getPrevPlusPort() {
					return prevPlusPort;
				}

				public void setPrevPlusPort(String prevPlusPort) {
					this.prevPlusPort = prevPlusPort;
				}

				public String getPrevPlusPortHashed() {
					return prevPlusPortHashed;
				}

				public void setPrevPlusPortHashed(String prevPlusPortHashed) {
					this.prevPlusPortHashed = prevPlusPortHashed;
				}

				public String getNextPortHashed() {
					return nextPortHashed;
				}

				public void setNextPortHashed(String nextPortHashed) {
					this.nextPortHashed = nextPortHashed;
				}

				public String getNextPlusPortHashed() {
					return nextPlusPortHashed;
				}

				public void setNextPlusPortHashed(String nextPlusPortHashed) {
					this.nextPlusPortHashed = nextPlusPortHashed;
				}

				public String getPrevPortHashed() {
					return prevPortHashed;
				}

				public void setPrevPortHashed(String prevPortHashed) {
					this.prevPortHashed = prevPortHashed;
				}

				public String getPrevPort() {
					return prevPort;
				}

				public void setPrevPort(String prevPort) {
					this.prevPort = prevPort;
				}


				public String getMyPortHashed() {
					return myPortHashed;
				}

				public void setMyPortHashed(String myPortHash) {
					this.myPortHashed = myPortHash;
				}

				public String getMyEmulatorId() {
					return myEmulatorId;
				}

				public void setMyEmulatorId(String myEmulatorId) {
					this.myEmulatorId = myEmulatorId;
				}

				public String getMyPort() {
					return myPort;
				}

				public void setMyPort(String myPort) {
					this.myPort = myPort;
				}


				public String getNextPort() {
					return nextPort;
				}

				public void setNextPort(String nextPort) {
					this.nextPort = nextPort;
				}


				public String getNextPlusPort() {
					return nextPlusPort;
				}

				public void setNextPlusPort(String nextPlusPort) {
					this.nextPlusPort = nextPlusPort;
				}


			}


