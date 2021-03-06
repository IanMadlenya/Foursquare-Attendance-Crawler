/**  
 * SMART FP7 - Search engine for MultimediA enviRonment generated contenT
 * Webpage: http://smartfp7.eu
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 * 
 * The Original Code is Copyright (c) 2012-2014 the University of Glasgow
 * All Rights Reserved
 * 
 * Contributor(s):
 *  @author Romain Deveaud <romain.deveaud at glasgow.ac.uk>
 *  @author M-Dyaa Albakour <dyaa.albakour at glasgow.ac.uk>
 */

package eu.smartfp7.foursquare;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.smartfp7.foursquare.utils.Settings;
import eu.smartfp7.geo.GeoUtil;

/**
 * This class allows to obtain all the venues in a given city. It creates a grid
 * around a central point specified in the `etc/settings.json` file, and queries
 * the Foursquare API for each point of this grid.
 * The result of this crawl is written in the `.exhaustive_crawl/venues.json` file,
 * ideally you should not touch this file.
 *
 */
public class GetAllVenues { 

  /**
   * Creates a grid of points around the central point specified in the `etc/settings.json`
   * file. These points are all couples of latitude and longitude double numbers.
   * 
   */
  public static double[][][] createGridAround(double[] point, int m){
	double xAxis[][]  = new double[m*2+1][2]; 
	double yAxis[][]  = new double[m*2+1][2];


	for(int i=1;i<=m; i++){
	  double nextPoint[]= GeoUtil.nextPoint(point, 100*i, 0);			
	  xAxis[m+i] = nextPoint;			
	}

	for(int i=1;i<=m; i++){
	  double nextPoint[]= GeoUtil.nextPoint(point, 100*i, Math.PI/2);			
	  yAxis[m+i] = nextPoint;			
	}

	for(int i=1;i<=m; i++){
	  double nextPoint[]= GeoUtil.nextPoint(point, 100*i, Math.PI);			
	  xAxis[m-i] = nextPoint;			
	}

	for(int i=1;i<=m; i++){
	  double nextPoint[]= GeoUtil.nextPoint(point, 100*i, 3*Math.PI/2);			
	  yAxis[m-i] = nextPoint;			
	}

	xAxis[m]= point;
	yAxis[m]= point;

	int n = 2*m*2*m;
	double[][][] grid = new double[n][2][2];		
	for(int i=0;i<2*m; i++){
	  for(int j=0;j<2*m; j++){
		grid[i*2*m+j][0][0]= yAxis[i][0];
		grid[i*2*m+j][0][1]= xAxis[j][1];				
		grid[i*2*m+j][1][0]= yAxis[i+1][0];
		grid[i*2*m+j][1][1]= xAxis[j+1][1];
	  }
	}
	return grid;
  }
  
  /**
   * Searches all the Foursquare venues that are located within the bounding box
   * specified in parameters (i.e. `sw` and `ne`).
   */
  public static String search4SqVenues(double[] sw, double[] ne,
	  String clientId, String clientSecret) throws Exception {

	HttpsURLConnection c = null;

	InputStream is = null;
	int rc;

	String url="";

	/** This parameter represents the date of the Foursquare API version that we use. 
	 *  If you want to modify the source code, please see https://developer.foursquare.com/overview/versioning */
	String vParam = "20140801";			

	/** Formatting the two coordinates of the bounding box. */
	String swString=sw[0]+","+sw[1];
	String neString=ne[0]+","+ne[1];

	try {
	  url = "https://api.foursquare.com/v2/venues/search?intent=browse&limit=100"+
		  "&client_id=" + clientId +
		  "&client_secret=" + clientSecret+
		  "&sw="+URLEncoder.encode(swString,"UTF-8") + "&ne="+URLEncoder.encode(neString,"UTF-8")+
		  "&v="+vParam;
	} catch (UnsupportedEncodingException e1) {
	  e1.printStackTrace();
	}

	final StringBuilder out = new StringBuilder();
	try {
	  c = (HttpsURLConnection) (new URL(url)).openConnection();

	  c.setRequestMethod("GET");
	  c.setDoOutput(true);
	  c.setReadTimeout(20000);	             
	  c.connect();

	  // Getting the response code will open the connection,
	  // send the request, and read the HTTP response headers.
	  // The headers are stored until requested.
	  rc = c.getResponseCode();
	  if (rc != HttpsURLConnection.HTTP_OK)
		throw new Exception("Connection can not be made with the Foursquare service..");

	  is = c.getInputStream();

	  final char[] buffer = new char[2048];

	  final Reader in = new InputStreamReader(is, "UTF-8");
	  for (;;) {
		int rsz = in.read(buffer, 0, buffer.length);
		if (rsz < 0)
		  break;
		out.append(buffer, 0, rsz);
	  }
	  return out.toString();

	} catch (ClassCastException e) {
	  throw new IllegalArgumentException("Not an HTTP URL");
	} catch (MalformedURLException e) {			 
	  throw e;
	} catch (IOException e) {				
	  throw e;
	}
  }

  /**
   * This is the main crawling function. It iterates over all the points
   * of the grid generated by the `createGridAround` function, and retrieves
   * the venues from Foursquare.
   * The crawl can be restarted in case it crashes.
   * 
   */
  public static void incrementalFsqCrawl(String city, double[] point, String folder, String clientId, String clientSecret, int m) throws IOException{
	JsonParser parser = new JsonParser();
	
	// Creating the grid of points around the center of the city.
	double[][][] grid = createGridAround(point,m);
	
	Collection<String> venue_ids = new ArrayList<String>();
	String ids_file = Settings.getInstance().getFolder()+ city + File.separator + ".exhaustive_crawl" + File.separator + "venues.ids";
	
	// We get the ids of the venues that have already been crawled,
	// in order to avoid storing them again.
	if(new File(ids_file).exists()) {
	  BufferedReader city_file = new BufferedReader(new FileReader(ids_file));
	  String line = null;
	  while ((line = city_file.readLine()) != null)
		venue_ids.add(line);
	  city_file.close();
	}
	
	// Opening the file where the results of the crawl will be written.
	FileWriter rawVenuesWriter = new FileWriter(folder + city + File.separator + ".exhaustive_crawl" + File.separator + "venues.json",true);
	FileWriter venueIdsWriter  = new FileWriter(ids_file,true);

	// Iterating over all the points of the grid.
	for(int x = 0 ; x <grid.length ; ++x){
	  double[] sw= grid[x][0];
	  double[] ne= grid[x][1];
	  
	  String response=null;
	  try {
		// Retrieves all the Foursquare venues that are located nearby
		// the bounding box given as a parameter.
		response = search4SqVenues(sw,ne,clientId,clientSecret);
	  } catch (Exception e) {
		e.printStackTrace();
		continue;
	  }
	  
	  JsonObject jsonResponse;
	  try {
		jsonResponse= parser.parse(response).getAsJsonObject();
	  } catch (Exception e) {
		System.out.println(response);
		continue;
	  }

	  // Parse the JSON response to extract an array of venues.
	  JsonArray venuesArr = jsonResponse.get("response").getAsJsonObject().get("venues").getAsJsonArray();
	  for(int i = 0; i < venuesArr.size() ; ++i){
		 //Since we want popular venues, we heuristically filter venues that have
		 // less than 25 overall checkins.
		Venue v = new Venue(venuesArr.get(i).toString());
		if(v.getCheckincount() < 25 || venue_ids.contains(v.getId()))
		  continue;

		// Write to the output file.
		rawVenuesWriter.write(venuesArr.get(i).toString()+"\n");
	  }
	  
	  try {
		Thread.sleep(1000);
	  } catch (InterruptedException e) {
		e.printStackTrace();
	  }
	}
	
	venueIdsWriter.close();
	rawVenuesWriter.close();
  }

  
  
  public static void main(String[] args) throws IOException{
	String city = args[0];
	String folder = Settings.getInstance().getFolder();
	
	/**
	 * This parameter controls the size of the grid that we will create
	 * around the city.
	 * WARNING: if the city is either small or very large, you might want 
	 * to change the value of this parameter.
	 * 
	 * TODO: include it the Settings?
	 */
	Integer size = 100;
	
	// Get the central point of the city and the Foursquare API credentials, 
	// both specified in the Settings.
	double[] centroid = new double[]{Settings.getInstance().getCityCenterLat(city),Settings.getInstance().getCityCenterLng(city)};
	Map<String, String> credentials = Settings.getInstance().getCityCredentials(city);
	
	Settings.getInstance().checkFileHierarchy(city);

	// The crawler can run indefinitely in order to obtain a complete list
	// of venues.
	while(true){
	  try {
		incrementalFsqCrawl(city, centroid, folder,  credentials.get("client_id"),  credentials.get("client_secret"), size);
	  } catch (NumberFormatException e) {
		e.printStackTrace();
	  } catch (IOException e) {
		e.printStackTrace();
	  } catch(Exception e) {
		continue;
	  }

	  System.out.println("Finished crawling everything. Pausing 10 minutes before restarting...");

	  try {
		Thread.sleep(600000);
	  } catch (InterruptedException e) {
		e.printStackTrace();
	  }
	} // while
  } // main
} // class
