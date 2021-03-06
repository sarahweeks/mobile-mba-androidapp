package com.samknows.tests;

//import android.annotation.SuppressLint;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpTest extends Test {

	/*
	 * Http Status codes
	 */
	private static final int HTTPOK = 200;
	private static final int HTTPCONTINUE = 100;

	/*
	 * error codes and contraints
	 */
	private static final int BYTESREADERR = -1;
	public static final int MAXNTHREADS = 100;

	/*
	 * Messages regarding the status of the test
	 */
	private static final String HTTPGETRUN = "Running download test";
	private static final String HTTPGETDONE = "Download test completed";
	private static final String HTTPPOSTRUN = "Running upload test";
	private static final String HTTPPOSTDONE = "Upload completed";

	/*
	 * Test strings
	 */
	private static final String DOWNSTREAMSINGLE = "JHTTPGET";
	private static final String DOWNSTREAMMULTI = "JHTTPGETMT";
	private static final String UPSTREAMSINGLE = "JHTTPPOST";
	private static final String UPSTREAMMULTI = "JHTTPPOSTMT";
	/*
	 * Parameters name for the setParameter function
	 */
	private static final String DOWNSTREAM = "downstream";
	private static final String UPSTREAM = "upstream";

	public static final String JSON_TRANFERTIME = "transfer_time";
	public static final String JSON_TRANFERBYTES = "transfer_bytes";
	public static final String JSON_BYTES_SEC = "bytes_sec";
	public static final String JSON_WARMUPTIME = "warmup_time";
	public static final String JSON_WARMUPBYTES = "warmup_bytes";
	public static final String JSON_NUMBER_OF_THREADS = "number_of_threads";

	/*
	 * Time in microseconds
	 */
	private static long microTime() {
		return System.nanoTime() / 1000;
	}

	public HttpTest() {
	}

	public HttpTest(boolean downstream) {
		this.downstream = downstream;
	}

	public HttpTest(String direction) {
		setDirection(direction);
	}

	public String getStringID() {
		String ret = "";
		if (downstream) {
			if (nThreads == 1) {
				ret = DOWNSTREAMSINGLE;
			} else {
				ret = DOWNSTREAMMULTI;
			}
		} else {
			if (nThreads == 1) {
				ret = UPSTREAMSINGLE;
			} else {
				ret = UPSTREAMMULTI;
			}
		}
		return ret;
	}

	/*
	 * 
	 */
	@Override
	public int getNetUsage() {
		return transferBytes + warmupBytes;
	}

	// @SuppressLint("NewApi")
	@Override
	public boolean isReady() {
		if (target.length() == 0) {
			setError("Target empty");
			return false;
		}
		if (port == 0) {
			setError("Port is zero");
			return false;
		}
		if (warmupMaxTime == 0 && warmupMaxBytes == 0) {
			setError("No warmup parameter defined");
			return false;
		}
		if (transferMaxTime == 0 && transferMaxBytes == 0) {
			setError("No transfer parameter defined");
			return false;
		}
		if (!downstream && (sendDataChunkSize == 0 || postDataLength == 0)) {
			setError("Upload parameter missing");
			return false;
		}
		if (downstream && bufferSize == 0) {
			setError("Buffer size missing for download");
			return false;
		}
		if (nThreads < 1 && nThreads > MAXNTHREADS) {
			setError("Number of threads error, current is: " + nThreads
					+ ". Min " + 1 + " Max " + MAXNTHREADS + ".");
			return false;
		}
		return true;
	}

	@Override
	public boolean isSuccessful() {
		return testStatus.equals("OK");
	}

	private String getHeaderRequest() {
		String request = "GET /%s HTTP/1.1 \r\nHost: %s \r\nACCEPT: */*\r\n\r\n";
		return String.format(request, file, target);
	}

	private String postHeaderRequest() {
		StringBuilder sb = new StringBuilder();
		sb.append("POST / HTTP/1.1 \r\n");
		sb.append("Host :%s \r\n");
		sb.append("Accept: */*\r\n");
		sb.append("Content-Length: %s \r\n");
		sb.append("Content-Type: application/x-www-form-urlencoded\r\n");
		sb.append("Expect: 100-continue\r\n");
		sb.append("\r\n");
		return String.format(sb.toString(), target, postDataLength);
	}

	public int getSendBufferSize() {
		return sendBufferSize;
	}

	public int getReceiveBufferSize() {
		return receiveBufferSize;
	}

	@Override
	public void execute() {
		if (downstream) {
			infoString = HTTPGETRUN;
		} else {
			infoString = HTTPPOSTRUN;
		}
		start();
		Thread[] threads = new Thread[nThreads];
		for (int i = 0; i < nThreads; i++) {
			threads[i] = new Thread(this);
			threads[i].start();
		}
		try {
			for (int i = 0; i < nThreads; i++) {
				threads[i].join();
			}
		} catch (Exception e) {
			setErrorIfEmpty("Thread join execption: ", e);
			testStatus = "FAIL";
		}

		if (downstream) {
			infoString = HTTPGETDONE;
		} else {
			infoString = HTTPPOSTDONE;
		}

		output();

		finish();
	}

	public String getInfo() {
		return infoString;
	}

	private Socket getSocket() {
		Socket ret = null;
		try {
			InetSocketAddress sockAddr = new InetSocketAddress(target, port);
			ipAddress = sockAddr.getAddress().getHostAddress();
			ret = new Socket();
			ret.setTcpNoDelay(noDelay);
			if (0 != desideredReceiveBufferSize) {
				ret.setReceiveBufferSize(desideredReceiveBufferSize);
			}
			if (0 != desideredSendBufferSize) {
				ret.setSendBufferSize(desideredSendBufferSize);
			}
			sendBufferSize = ret.getSendBufferSize();
			receiveBufferSize = ret.getReceiveBufferSize();
			ret.setSoTimeout(Test.READTIMEOUT);
			ret.connect(sockAddr, Test.CONNECTIONTIMEOUT);
		} catch (Exception e) {
			e.printStackTrace();
			ret = null;
		}
		return ret;
	}

	private SocketAddress InetSocketAddress(String target2, int port2) {
		// TODO Auto-generated method stub
		return null;
	}

	// Bytes per second
	public int getSpeed() {
		if (transferTime == 0) {
			return 0;
		}
		return (int) (((transferBytes) / ((double) transferTime) * 1000000));
	}

	public synchronized int getTransferBytes() {
		return warmupBytes + transferBytes;
	}

	public synchronized int getWarmupBytes() {
		return warmupBytes;
	}

	public String getHumanReadableResult() {
		String ret = "";
		String direction = downstream ? "download" : "upload";
		String type = nThreads == 1 ? "single connection"
				: "multiple connection";
		if (testStatus.equals("FAIL")) {
			ret = String.format("The %s has failed.", direction);
		} else {
			ret = String.format("The %s %s test achieved %.2f Mbps.", type,
					direction, (getSpeed() * 8d / 1000000));
		}
		return ret;
	}

	@Override
	public HumanReadable getHumanReadable() {
		HumanReadable ret = new HumanReadable();
		if (downstream) {
			if (testStatus.equals("FAIL")) {
				ret.testString = TEST_STRING.DOWNLOAD_FAILED;
			} else if (nThreads == 1) {
				ret.testString = TEST_STRING.DOWNLOAD_SINGLE_SUCCESS;
				ret.values = new String[1];
				ret.values[0] = String.format("%.2f",
						(getSpeed() * 8d / 1000000));
			} else {
				ret.testString = TEST_STRING.DOWNLOAD_MULTI_SUCCESS;
				ret.values = new String[1];
				ret.values[0] = String.format("%.2f",
						(getSpeed() * 8d / 1000000));
			}
		} else {
			if (testStatus.equals("FAIL")) {
				ret.testString = TEST_STRING.UPLOAD_FAILED;
			} else if (nThreads == 1) {
				ret.testString = TEST_STRING.UPLOAD_SINGLE_SUCCESS;
				ret.values = new String[1];
				ret.values[0] = String.format("%.2f",
						(getSpeed() * 8d / 1000000));
			} else {
				ret.testString = TEST_STRING.UPLOAD_MULTI_SUCCESS;
				ret.values = new String[1];
				ret.values[0] = String.format("%.2f",
						(getSpeed() * 8d / 1000000));
			}
		}
		return ret;
	}

	private void output() {
		ArrayList<String> o = new ArrayList<String>();
		Map<String, String> output = new HashMap<String, String>();
		// string id
		String value = getStringID();
		o.add(value);
		output.put(Test.JSON_TYPE, value);
		// time
		long time_stamp = unixTimeStamp();
		o.add(time_stamp + "");
		output.put(Test.JSON_TIMESTAMP, time_stamp + "");
		output.put(Test.JSON_DATETIME,
				new java.util.Date(time_stamp * 1000).toString());
		// status
		if (error.get()) {
			o.add("FAIL");
			output.put(Test.JSON_SUCCESS, Boolean.toString(false));
		} else {
			o.add("OK");
			output.put(Test.JSON_SUCCESS, Boolean.toString(true));
		}
		// target
		o.add(target);
		output.put(Test.JSON_TARGET, target);
		// target ip address
		o.add(ipAddress);
		output.put(Test.JSON_TARGET_IPADDRESS, ipAddress);
		// transfer time
		value = Long.toString(transferTime);
		o.add(value);
		output.put(JSON_TRANFERTIME, value);
		// transfer bytes
		value = Integer.toString(transferBytes);
		o.add(value);
		output.put(JSON_TRANFERBYTES, value);
		// byets_sec
		value = Integer.toString(getSpeed());
		o.add(value);
		output.put(JSON_BYTES_SEC, value);
		// warmup time
		value = Long.toString(warmupTime);
		o.add(value);
		output.put(JSON_WARMUPTIME, value);
		// warmup bytes
		value = Integer.toString(warmupBytes);
		o.add(value);
		output.put(JSON_WARMUPBYTES, value);
		// number of threads
		value = Integer.toString(nThreads);
		o.add(value);
		output.put(JSON_NUMBER_OF_THREADS, value);

		setOutput(o.toArray(new String[1]));
		setJSONOutput(output);
	}

	public void download() {
		Socket conn = null;
		OutputStream connOut = null;
		InputStream connIn = null;
		byte[] buff = new byte[bufferSize];
		int readBytes = 0;
		conn = getSocket();
		if (conn != null) {
			try {

				connOut = conn.getOutputStream();
				connIn = conn.getInputStream();
				PrintWriter writerOut = new PrintWriter(connOut, false);
				writerOut.print(getHeaderRequest());
				writerOut.flush();
				int httpResponse = readResponse(connIn);
				if (httpResponse != HTTPOK) {
					setErrorIfEmpty("Http response received: " + httpResponse);
					error.set(true);
				}
			} catch (IOException io) {
				error.set(true);
			}
		} else {
			error.set(true);
		}
		waitForAllConnections();
		if (error.get()) {
			closeConnection(conn, connIn, connOut);
			return;
		}
		// warmup, can be based on time constraint or data usage constraint
		do {
			try {
				readBytes = connIn.read(buff, 0, buff.length);
			} catch (IOException io) {
				readBytes = BYTESREADERR;
				error.set(true);
			}
		} while (!isWarmupDone(readBytes));
		if (error.get()) {
			closeConnection(conn, connIn, connOut);
			return;
		}
		do {
			try {
				readBytes = connIn.read(buff, 0, buff.length);
			} catch (IOException io) {
				readBytes = BYTESREADERR;
			}
		} while (!isTransferDone(readBytes));

		closeConnection(conn, connIn, connOut);

	}

	synchronized void waitForAllConnections() {
		connectionCounter++;
		if (connectionCounter < nThreads) {
			try {
				wait();
			} catch (InterruptedException e) {
				error.set(true);
			}
		} else {
			notifyAll();
		}
	}

	// the warmup ends also if there is a problem on any connection
	synchronized boolean isWarmupDone(int bytes) {
		boolean ret = false;
		boolean timeWarmup = false;
		boolean bytesWarmup = false;
		// if there is an error the test must stop and report it
		if (bytes == BYTESREADERR) {
			setErrorIfEmpty("read error");
			bytes = 0; // do not modify the bytes counters
			error.set(true);
		}

		warmupBytes += bytes;
		if (startWarmup == 0) {
			startWarmup = microTime();
		}
		warmupTime = microTime() - startWarmup;
		// if warmup max time is set and time has exceeded its values set time
		// warmup to true
		timeWarmup = warmupMaxTime > 0 && warmupTime >= warmupMaxTime;

		// if warmup max bytes is set and bytes counter exceeded its value set
		// bytesWarmup to true
		bytesWarmup = warmupMaxBytes > 0 && warmupBytes >= warmupMaxBytes;

		// if a condition happened increment the warmupDoneCounter
		if (timeWarmup || bytesWarmup) {
			warmupDoneCounter++;
			ret = true;
		}
		// if there is an error notify all, some thread might be waiting for
		// other to finish the warmup, and exit
		if (error.get()) {
			notifyAll();
			ret = true;

		}// warmup is finished, wait for other threads if any
		else if (ret && warmupDoneCounter < nThreads) {
			startTransfer = microTime();
			try {
				wait();
			} catch (InterruptedException ie) {
				error.set(true);
				ret = true;
				notifyAll();
			}
		}// warmup is finished, last thread, notify all
		else if (ret) {
			notifyAll();
		}
		return ret;

	}

	synchronized boolean isTransferDone(int bytes) {
		boolean ret = false;
		// In case an error occurred stop the test
		if (bytes == BYTESREADERR) {
			error.set(true);
			bytes = 0;
		}
		transferBytes += bytes;

		// if startTransfer is 0 this is the first call to isTransferDone
		if (startTransfer == 0) {
			startTransfer = microTime();
		}
		transferTime = microTime() - startTransfer;
		// if transfermax time is
		if ((transferMaxTime > 0) && (transferTime > transferMaxTime)) {
			ret = true;
		}
		if ((transferMaxBytes > 0)
				&& (transferBytes + warmupBytes > transferMaxBytes)) {
			ret = true;
		}
		if (transferBytes > 0) {
			testStatus = "OK";
		}

		if (error.get()) {
			ret = true;
		}

		return ret;
	}

	@Override
	public void run() {
		if (downstream) {
			download();
		} else {
			upload();
		}
	}

	// Reads the http response and returns the http status code
	private int readResponse(InputStream is) {
		int ret = 0;
		try {
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(is));
			String line = reader.readLine();
			if (line != null && line.length() != 0) {
				int s = line.indexOf(' ');
				int f = line.indexOf(' ', ++s);
				ret = Integer.parseInt(line.substring(s, f));
			}

			while ((line = reader.readLine()) != null) {
				if (line.length() == 0) {
					break;
				}
			}
		} catch (IOException IOe) {
			setErrorIfEmpty("IOexception while reading http header: ", IOe);
			ret = 0;
		} catch (NumberFormatException nfe) {
			setErrorIfEmpty("Error in converting the http code: ", nfe);
			ret = 0;
		}
		return ret;
	}

	public void upload() {
		Socket conn = null;
		OutputStream connOut = null;
		InputStream connIn = null;
		Random generator = new Random();
		byte[] buff = new byte[sendDataChunkSize];
		conn = getSocket();
		if (conn != null) {
			try {
				connOut = conn.getOutputStream();
				connIn = conn.getInputStream();
				PrintWriter writerOut = new PrintWriter(connOut, false);
				writerOut.print(postHeaderRequest());
				writerOut.flush();
				int httpResponseCode = readResponse(connIn);
				if (httpResponseCode != HTTPCONTINUE) {
					setErrorIfEmpty("Http response received: "
							+ httpResponseCode);
					error.set(true);
				}
			} catch (IOException ioe) {
				error.set(true);
			}
		} else {
			error.set(true);
		}
		waitForAllConnections();
		if (error.get()) {
			closeConnection(conn, connIn, connOut);
			return;
		}
		do {
			if (randomEnabled) {
				generator.nextBytes(buff);
			}
			try {
				connOut.write(buff);
				connOut.flush();
			} catch (IOException ioe) {
				error.set(true);
			}
		} while (!isWarmupDone(sendDataChunkSize));
		if (error.get()) {
			closeConnection(conn, connIn, connOut);
			return;
		}
		do {
			if (randomEnabled) {
				generator.nextBytes(buff);
			}
			try {
				connOut.write(buff);
				connOut.flush();
			} catch (IOException ioe) {
				error.set(true);
			}
		} while (!isTransferDone(sendDataChunkSize));
		closeConnection(conn, connIn, connOut);

		return;

	}

	private void closeConnection(Socket s, InputStream i, OutputStream o) {
		try {
			if(i != null){
				i.close();
			}
			if(o != null){
				o.close();
			}
			if(s != null){
				s.close();
			}
		} catch (IOException ioe) {

		}
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setFile(String file) {
		this.file = file;
	}

	public void setWarmupMaxTime(int time) {
		warmupMaxTime = time;
	}

	public void setWarmupMaxBytes(int bytes) {
		warmupMaxBytes = bytes;
	}

	public void setTransferMaxTime(int time) {
		transferMaxTime = time;
	}

	public void setTransferMaxBytes(int bytes) {
		transferMaxBytes = bytes;
	}

	public void setBufferSize(int size) {
		bufferSize = size;
	}

	public void setSendBufferSize(int size) {
		desideredSendBufferSize = size;
	}

	public void setReceiveBufferSize(int size) {
		desideredReceiveBufferSize = size;
		bufferSize = size;
	}

	public void setSendDataChunk(int size) {
		sendDataChunkSize = size;
	}

	public void setPostDataLenght(int l) {
		postDataLength = l;
	}

	public void setDownstream() {
		downstream = true;
	}

	public void setUpstream() {
		downstream = false;
	}

	public void setNumberOfThreads(int n) {
		nThreads = n;
	}

	public void setDirection(String d) {
		if (Test.paramMatch(d, DOWNSTREAM)) {
			downstream = true;
		} else if (Test.paramMatch(d, UPSTREAM)) {
			downstream = false;
		}
	}

	public boolean isProgressAvailable() {
		boolean ret = false;
		if (transferMaxTime > 0) {
			ret = true;
		} else if (transferMaxBytes > 0) {
			ret = true;
		}
		return ret;
	}

	public int getProgress() {
		double ret = 0;

		synchronized (this) {
			if (startWarmup == 0) {
				ret = 0;
			} else if (transferMaxTime != 0) {
				long currTime = microTime() - startWarmup;
				ret = (double) currTime / (warmupMaxTime + transferMaxTime);

			} else {
				int currBytes = warmupBytes + transferBytes;
				ret = (double) currBytes / (warmupMaxBytes + transferMaxBytes);
			}
		}
		ret = ret < 0 ? 0 : ret;
		ret = ret >= 1 ? 0.99 : ret;
		return (int) (ret * 100);
	}

	boolean upload = true;
	boolean randomEnabled = false;
	String infoString = "";
	String ipAddress = "";
	boolean warmUpDone = false;
	int postDataLength = 0;

	// warmup variables
	long startWarmup = 0;
	long warmupTime = 0;
	long warmupMaxTime = 0;
	int warmupBytes = 0;
	int warmupMaxBytes = 0;
	int warmupDoneCounter = 0;

	// transfer variables
	long startTransfer = 0;
	long transferTime = 0;
	int transferBytes = 0;
	long transferMaxTime = 0;
	int transferMaxBytes = 0;
	int transferDoneCounter = 0;

	// test variables
	int connectionCounter = 0;
	int nThreads;
	boolean noDelay = false;
	int receiveBufferSize = 0;
	int sendBufferSize = 0;
	int bufferSize = 0;
	int desideredReceiveBufferSize = 0;
	int desideredSendBufferSize = 0;
	int sendDataChunkSize = 0;
	String testStatus = "FAIL";

	// Connection variables
	String target = "";
	String file = "";
	int port = 0;
	boolean downstream = true;

	// Test Status variable
	private AtomicBoolean error = new AtomicBoolean(false);

}
