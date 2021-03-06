package com.samknows.measurement.net;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import com.samknows.measurement.AppSettings;
import com.samknows.measurement.Logger;
import com.samknows.measurement.storage.ResultsContainer;
import com.samknows.measurement.test.TestResultsManager;

import android.content.Context;
import android.net.Uri;

public class SubmitTestResultsAnonymousAction {
	protected Context context;
	protected boolean isSuccess = false;

	public SubmitTestResultsAnonymousAction(Context _context) {
		context = _context;
	}

	public void execute() {
		String[] results = TestResultsManager.getJSONData(context);
		List<Integer> fail = new ArrayList<Integer>();
		for (int i=0; i<results.length; i++) {
			byte[] data = results[i].getBytes();
			if (data == null) {
				Logger.d(SubmitTestResultsAnonymousAction.class,
						"no results to be submitted");
				break;
			}
			HttpContext httpContext = new BasicHttpContext();
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(buildUrl());
			httpPost.setEntity(new ByteArrayEntity(data));
			httpContext.setAttribute("Content-Length", data.length);
			try {
				HttpResponse httpResponse = httpClient.execute(httpPost);
				StatusLine sl = httpResponse.getStatusLine();
				isSuccess = sl.getStatusCode() == HttpStatus.SC_OK
						&& sl.getReasonPhrase().equals("OK");
				int code = sl.getStatusCode();
				Logger.d(this, "submiting test results to server: " + isSuccess);
			} catch (Exception e) {
				Logger.e(this, "failed to submit results to server", e);
				isSuccess = false;
			}

			if (!isSuccess) {
				fail.add(i);
				TestResultsManager.clearResults(context);
				TestResultsManager.saveSumbitedLogs(context, data);
			}
		}
		TestResultsManager.clearResults(context);
		for(int i:fail){
			TestResultsManager.saveResult(context, results[i]);
		}
	}

	public String buildUrl() {
		AppSettings settings = AppSettings.getInstance();
		return new Uri.Builder().scheme(settings.protocol_scheme)
				.authority(settings.getServerBaseUrl())
				.path(settings.submit_path).build().toString();
	}
}
