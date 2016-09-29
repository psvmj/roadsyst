package com.autoStation.serv.mongodb;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.bson.Document;

import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;

public class ServSide {
	
	private ObjectInputStream input1;
	private ObjectOutputStream output1;
	private ServerSocket server;
	private Socket connection;
    private	Date timeIn;
	private String dateOutSave;
	private String pathOfTheDriver="";
	private SimpleDateFormat timeFormat = new SimpleDateFormat("YYYY-MM-dd'T'hh:mm:ss");//date's format
	private SimpleDateFormat timeFormat2 = new SimpleDateFormat("YYYY-MM-dd hh:mm:ss");
	
public void startServ(){  //setting up server
		try{
			server = new ServerSocket(9988);
			try{ 
				while(true){//condition to stop server. could be anything
					
					waitForConnection(); //waiting for client to connect
					setupStreams(); //starting streams
					Calculating(); //calculating charge, sending email and confirmation answer					
				}
				
			}catch (EOFException e){
				e.printStackTrace();
				
			} finally {
				closeAll();
			}
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		
	}

public void closeAll() throws IOException{
	
	 showMessage("Closing all");
	 input1.close();
	 output1.flush();
	 connection.close();
	
}
public void sendSummary(String email, String roadName, Date dateIn, String dateOut, String charge) throws IOException{
	
	//sending summary message to the client console
	
	String msgFromServer = String.format("%n=================================%n"
			+ " +++++USER'S CONSOLE MESSAGE+++++"
			+ "%n Confirmation email sent to:%s"
			+ "%n check-in date/time:%s "
			+ "%n check-out date/time:%s "
			+ "%n Total for this trip: %s "
			+ "%n You have traveled over: %s. "
			+ "%n Have a nice day! "
			+ "%n ++++END OF USER'S CONSOLE MESSAGE+++++ "
			+ "%n=================================%n",      
			email, timeFormat2.format(dateIn), dateOut,charge, roadName);
	
	output1.writeObject(msgFromServer); //data sent
	
}

public void waitForConnection() throws IOException{  //waiting for someone to connect
	
	showMessage("waiting for someone to connect...");
	connection = server.accept();
	showMessage("now connected to " + connection.getInetAddress().getHostName());

}

public void setupStreams() throws IOException{ //setting up streams
	
	output1 = new ObjectOutputStream(connection.getOutputStream());
	output1.flush();
	input1 = new ObjectInputStream(connection.getInputStream());
	showMessage("streams are now ready");
	
}

public void showMessage(final String text) {//procedure for showing server side messages. gets a string to show as a parameter
	 
	System.out.println("Server log message: " + text);
	 
}

public void Calculating() throws IOException {
	
ArrayList<Integer> details; //data received from client
Integer user_id; //user index in db
Integer activity_id;//trip index for particular user

String email; //user email to be fetched
Double charge = 0.00;//initial total

  showMessage("connecting... ");
  try{
	  
		details = (ArrayList<Integer>) input1.readObject();   //getting data from client
		  user_id = details.get(0); //user_id index from client
		//connecting to a db
		  MongoClient mongo = new MongoClient( "localhost" , 27017 );
	      MongoDatabase db =  mongo.getDatabase("stations_db");
	      
	      showMessage("connected to a db. Starting payment confirmation...");//server side log
	      
	      //fetching email
	      FindIterable<Document> iterable_email = db.getCollection(
	    		  "users_list").find(
	    				  new Document("user_id", user_id));  
	      Document resultSet =  iterable_email.first();
	      email = (String) resultSet.get("email");
	      
	      showMessage("Confirmation email will be send to: " +email);//server side log message
	      
	      //generating check-out date
	      Date dateOut = new Date(System.currentTimeMillis());//converting timestamp into a date type
		  String dateOutSave = timeFormat2.format(dateOut);//check-out date to be saved in dbs
			 
		  //fetching latest user's activity
	      FindIterable<Document> iterable_activity = db.getCollection(
	    		  "activity_list").find(
	    				  new Document("user_id", user_id)).limit(1).sort(new Document("activity_id", -1));     
	      resultSet =  iterable_activity.first();
	      activity_id =  (Integer) resultSet.get("activity_id");
	      //fetching first check-in date for this particular trip(date when system created new trip)
	      
	      FindIterable<Document> iterable_timeIn = db.getCollection(
	    		  "activity_list").find(
	    				  new Document("activity_id", activity_id)).sort(new Document("time_in", 1));  
	      resultSet =  iterable_timeIn.first();
	      timeIn = timeFormat.parse( resultSet.getString("time_in"));
	      
	      showMessage("check-in date/time: "+timeFormat2.format(timeIn));//server side log
	      
	      //saving check-out date
		  db.getCollection("activity_list").updateOne(
				  new Document("activity_id", activity_id),
			                      new Document("$set", new Document("time_out", dateOutSave)));
		  
		  showMessage("check-out date/time: "+dateOutSave);//server side log
		  
		  final ArrayList<Integer> listOfRoads = new ArrayList<Integer>();
	      final ArrayList<String>  listOfNames = new ArrayList<String>();
	      final ArrayList<Integer> listOfRates = new ArrayList<Integer>();
	      //fetching list of roads(stations) which user has passed before go out.
		  FindIterable<Document> iterable_roads = db.getCollection(
				  "activity_list").find(new Document("user_id", user_id).
						  append("activity_id", activity_id));  
		  
	      iterable_roads.forEach(new Block<Document>() {	   
	    	    public void apply(final Document document) {
	    	        listOfRoads.add((Integer) document.get("road_id"));
	    	    }
	    	});
	      //fetching charge rates for each station(road)
	      for (Integer x:listOfRoads) 
	      {	  
	      FindIterable<Document> iterable_chargeAndRoads = db.getCollection(
	    		  "stations_list").find(new Document("station_id", x));
	      
	      iterable_chargeAndRoads.forEach(new Block<Document>() {	   
	    	    public void apply(final Document document) {
	    	        listOfNames.add((String) document.get("road_name"));
	    	        listOfRates.add((Integer) document.get("charge_rate"));
	    	    }
	    	});
	       }
	      //generating string for output of the path for driver
	      for(String s:listOfNames){
	    	  pathOfTheDriver = pathOfTheDriver+s+" => ";  
	      }
	      //calculating charge amount
	      for(Integer c:listOfRates){
	    	  charge = charge +(double)c*0.95;  
	      }
	      
		  /*
		   * 
		   * any calculations for the charge variable
		   * 
		   */
	       charge = (double) Math.round((charge*100.00))/100.00;
	     //saving payed amount into a db  
	       db.getCollection("paid_list").insertOne(
	    	        new Document("user_id", user_id).append("activity_id", activity_id).append("paid", charge));
	    	                
	       
	       showMessage(pathOfTheDriver);//server side log
	       showMessage("Total for this trip "+ charge);//server side log
		
		 sendSummary(email, pathOfTheDriver, timeIn, dateOutSave, charge.toString()); //setting up message for user's console
		sendEmail(email, charge); //sending email confirmation
		mongo.close();//closing db connection
		listOfNames.clear();
		listOfRates.clear();
		pathOfTheDriver="";//clear path
		
  }catch(Exception e){
	  e.printStackTrace();
  }
		
	}



public void sendEmail(String email, Double charge){

	 String to = email;//fetched email
     String from = "station@roadserv.com";//valid email
     String host = "localhost";//valid smtp 
     Properties properties = System.getProperties();
     properties.setProperty("mail.smtp.host", host);   //VALID SMTP
     properties.setProperty("mail.smtp.port", "25");   //valid smtp port
     Session session = Session.getInstance(properties);

     try {
     
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        // Set To: header field of the header.
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        // Set Subject: header field
        message.setSubject("Auto station payment");
        // Now set the actual message
        message.setText("Dear Driver, Good Day! %n You have traveled along:"+pathOfTheDriver+".%nYour check-in date/time:"+ 
        		timeFormat2.format(timeIn)+".%n Your check-out date/time:"+dateOutSave+". "
                         + "%n Total paid: "+charge+" %n Have a nice day!");
        // Send message
        Transport.send(message);
        showMessage("Confirmation email sent.");
     }catch (Exception e) {
        System.out.println("Please configure smtp properly");
     }finally {
    	 
     }
	
}


}

