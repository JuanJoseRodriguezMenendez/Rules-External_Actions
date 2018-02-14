package fiwoo.microservices.rules_External_Actions.fiwoo_rules_External_Actions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;

import fiwoo.microservices.rules_External_Actions.dao.RuleDBDAO;
import fiwoo.microservices.rules_External_Actions.model.RuleDB;

public class Logic {
	
	private static final String DEFAULT_DURATION = "P1Y";
	private static final String DEFAULT_THROTTLING = "PT1S";
	
	// read from environment 
	
	private static final String DEFAULT_PERSEO_HOST = "172.17.0.5";
	private static final String DEFAULT_PERSEO_PORT = "9090";
	private static final String DEFAULT_ORION_HOST = "0.0.0.0";
	private static final String DEFAULT_ORION_PORT = "1026";
	private static final String DEFAULT_FIWARE_SERVICE ="unknownT" ;
	private static final String DEFAULT_SERVICE_PATH ="/";
	
	// Context info to connect to data base.
	private static ApplicationContext context;
	private static RuleDBDAO ruleDAO;

	public String orion_host;
	public String orion_port;
	public String perseo_host;
	public String perseo_port;
	
	public String fiware_service;
	public String fiware_servicePath;

	public Logic() {
		// Initialize Variables
		
		// set perseo host and port
		if (perseo_host == null || perseo_host.isEmpty())
			perseo_host = DEFAULT_PERSEO_HOST;
		if (perseo_port == null || perseo_port.isEmpty())
			perseo_port = DEFAULT_PERSEO_PORT;
		
		//set orion host, port and services
		fiware_service = DEFAULT_FIWARE_SERVICE;
		fiware_servicePath = DEFAULT_SERVICE_PATH;
		orion_host = DEFAULT_ORION_HOST;
		orion_port = DEFAULT_ORION_PORT;
		// Check connections to perseo and orion
		
		ApplicationContext context = new ClassPathXmlApplicationContext("Spring-Module.xml");
	    ruleDAO = (RuleDBDAO) context.getBean("ruleDBDAO");
	}
	
	public Logic(String orion_host, String orion_port, String perseo_host, String perseo_port, String fiware_service,
			String fiware_servicePath) {
		super();
		// set perseo host and port
		this.perseo_host = (perseo_host == null || perseo_host.isEmpty())?DEFAULT_PERSEO_HOST:perseo_host;
		this.perseo_port = (perseo_port == null || perseo_port.isEmpty())?DEFAULT_PERSEO_PORT:perseo_port;
		
		this.orion_host = (orion_host == null || orion_host.isEmpty())?DEFAULT_ORION_HOST:orion_host;
		this.orion_port = (orion_port == null || orion_port.isEmpty())?DEFAULT_ORION_PORT:orion_port;
		
		this.fiware_service = (fiware_service == null || fiware_service.isEmpty())?DEFAULT_FIWARE_SERVICE:fiware_service;
		this.fiware_servicePath = (fiware_servicePath == null || fiware_servicePath.isEmpty())?DEFAULT_FIWARE_SERVICE:fiware_servicePath;
		
		ApplicationContext context = new ClassPathXmlApplicationContext("Spring-Module.xml");
	    RuleDBDAO ruleDAO = (RuleDBDAO) context.getBean("ruleDBDAO");
	}
	
	public String getOrion_host() {
		return orion_host;
	}

	public void setOrion_host(String orion_host) {
		this.orion_host = orion_host;
	}

	public String getOrion_port() {
		return orion_port;
	}

	public void setOrion_port(String orion_port) {
		this.orion_port = orion_port;
	}

	public String getPerseo_host() {
		return perseo_host;
	}

	public void setPerseo_host(String perseo_host) {
		this.perseo_host = perseo_host;
	}

	public String getPerseo_port() {
		return perseo_port;
	}

	public void setPerseo_port(String perseo_port) {
		this.perseo_port = perseo_port;
	}

	public String getFiware_service() {
		return fiware_service;
	}

	public void setFiware_service(String fiware_service) {
		this.fiware_service = fiware_service;
	}

	public String getFiware_servicePath() {
		return fiware_servicePath;
	}

	public void setFiware_servicePath(String fiware_servicePath) {
		this.fiware_servicePath = fiware_servicePath;
	}
	
	public static ApplicationContext getContext() {
		return context;
	}

	public static void setContext(ApplicationContext context) {
		Logic.context = context;
	}

	public static RuleDBDAO getRuleDAO() {
		return ruleDAO;
	}

	public static void setRuleDAO(RuleDBDAO ruleDAO) {
		Logic.ruleDAO = ruleDAO;
	}

	public String parseAdvancedRule(String ruleJson, String user_id) {
		String result = "";
		Gson gson = new Gson();
		gson.serializeNulls();
		Object rule = gson.fromJson(ruleJson, Object.class);
		LinkedTreeMap<Object, Object> ruleMap = (LinkedTreeMap<Object, Object>) rule;
		
		// Check if Rule exist
		String oldName = ruleMap.get("name").toString();
		if (existsRule(oldName, user_id)) return "Rule already exist.";
		
		// Parse Rule to extract an orion subscription
		String text = ruleMap.get("text").toString();
		//I have to extract from here: Entity Type + attributes + Entity id if it exist
		//   "text": "select *,\"blood_rule_update\" as ruleName from pattern 
		//[every ev=iotEvent(cast(cast(BloodPressure?,String),float)>1.5 and type=\"BloodMeter\" and cast(id?,String)=\"bloodm1\")]",
		
		// get all attributes
		List<String> attributesToChange = new ArrayList<String>();
		String[] splited = text.split("[?]");
		String id = ".*";
		for (int i=0; i<splited.length-1;i++) {
			String s = splited[i];
			String at = s.substring(s.lastIndexOf("(")+1);
			if (!at.equals("id")) {
				attributesToChange.add(at);
			} else {
				// id found. Save it to do the subscription
				id = splited[i+1].substring(splited[i+1].indexOf("\"")+1, splited[i+1].lastIndexOf("\""));
			}
		}
		
		// search for Type
		String[] typeSplit = text.split("type=\"");
		String type = typeSplit[1].substring(0, typeSplit[1].indexOf("\""));
		
		
		//change name of the rule to match user_ruleName
		ruleJson = changeRuleName(ruleJson, user_id, oldName);
		
		// Create the orion subscription
		OrionSubscription subscription = createOrionSubscription(id, type, attributesToChange, "", "", "");
		
		// Send subscription to orion
		String subscription_result = sendSubscription(subscription);
		LinkedTreeMap<Object, Object> subscriptionResultMap = (LinkedTreeMap<Object, Object>) gson.fromJson(subscription_result, Object.class);
		String subscriptionId = "";
		if (subscriptionResultMap.get("subscribeError") == null) {
			// No error
			subscriptionId = ((LinkedTreeMap<Object, Object>)subscriptionResultMap.get("subscribeResponse")).get("subscriptionId").toString();
			result = subscriptionResultMap.toString();
		} else {
			// Error
			return subscriptionResultMap.toString();
		}
		
		
		
		// Send Rule
		String rule_result = sendRule(ruleJson);
		LinkedTreeMap<Object, Object> ruleResultMap = (LinkedTreeMap<Object, Object>) gson.fromJson(rule_result, Object.class);
		if (ruleResultMap.get("error") == null) {
			// no hay error
			result += "\n rule created :)";
		} else
		{
			// delete created subscription
			deleteSubscription(subscriptionId);
			return rule_result;
		}
		
		// Everything ok. Store in database and return result
		ruleDAO.insert(new RuleDB(createRuleId(user_id, oldName), user_id, oldName, "No description", ruleJson, subscriptionId));
		
		// Store the id of the rule
		//else: return error in rule, delete subscription if it was created.
		return result;
		
	}
	
	public boolean existsRule(String rule_name, String user_id) {
		return ruleDAO.existsRule(createRuleId(user_id,rule_name));
	}
	
	public String createRuleId(String user_id, String rule_name) {
		return user_id + "_" + rule_name;
	}
	
	public String changeRuleName (String ruleJson, String user_id, String rule_name) {
		String rule_id = createRuleId(user_id, rule_name);
		Gson gson = new Gson();
		gson.serializeNulls();
		Object rule = gson.fromJson(ruleJson, Object.class);
		LinkedTreeMap<Object, Object> ruleMap = (LinkedTreeMap<Object, Object>) rule;
		
		ruleMap.remove("name");
		ruleMap.put("name", rule_id);
		
		String text = (String) ruleMap.get("text");
		//"text": "select *,\"rule_name\" as ruleName from pattern 
		text = text.replaceFirst("\""+rule_name+"\"","\""+rule_id+"\"");
		
		ruleMap.remove("text");
		ruleMap.put("text", text);
		
		
		return gson.toJson(ruleMap, LinkedTreeMap.class);
		}
	
//	public String parseUserRule(String ruleJson) {
//		
//	}
	
	// send rule  
	public String sendRule(String ruleJson) {
		
		String stringURL = "http://" + perseo_host+":"+perseo_port+"/rules";
		
        URL url;
        StringBuilder sb  = new StringBuilder();
        try {
            url = new URL(stringURL);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(15000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
           
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, "UTF-8"));
            
            writer.write(ruleJson);
            writer.flush();
            writer.close();
            os.close();

           
          //displays what the POST request returns

          sb = new StringBuilder();
          int HttpResult = conn.getResponseCode(); 
          // sb.append(HttpResult + " : " + conn.getResponseMessage());
          
          if (HttpResult == HttpURLConnection.HTTP_OK) {
              BufferedReader br = new BufferedReader(
                      new InputStreamReader(conn.getInputStream(), "utf-8"));
              String line = null;  
              while ((line = br.readLine()) != null) {  
                  sb.append(line + "\n");  
              }
              br.close();
              
          } else {
        	  if (conn.getErrorStream()!=null) {
        		  BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
        		  String line = null;  
                  while ((line = br.readLine()) != null) {  
                      sb.append(line + "\n");  
                  }
                  br.close();
        	  }
          }  
        } catch (Exception e) {
            e.printStackTrace();
        } 
        return sb.toString();
	}
	
	//create orion subscription
	public OrionSubscription createOrionSubscription(String entityID, String entityType, List<String> attributes, String reference, String duration, String throttling) {
		
		List<Entity> entities = new ArrayList<Entity>();
		entities.add(new Entity(entityType, true, entityID));
		if (reference == null || reference.isEmpty()) {
			if (perseo_host == null || perseo_host.isEmpty())
				perseo_host = DEFAULT_PERSEO_HOST;
			if (perseo_port == null || perseo_port.isEmpty())
				perseo_port = DEFAULT_PERSEO_PORT;
			reference = "http://"+perseo_host+":"+perseo_port+"/notices";
		}
		if (duration == null || duration.isEmpty())	duration = DEFAULT_DURATION;
		if (throttling == null || throttling.isEmpty()) throttling = DEFAULT_THROTTLING;
		
		List<NotifyCondition> notifiesConditions = new ArrayList<NotifyCondition>();
		
		notifiesConditions.add(new NotifyCondition("ONCHANGE", attributes));
		
		OrionSubscription orionSubscription = new OrionSubscription(entities, attributes, reference, duration, notifiesConditions, throttling);
	
		return orionSubscription;
		//now that the object was created, transform it to JSON and send to ORION.
		
		//This is the structure of the orion Subscription creation
		/*(curl ${ORION_HOST}:${ORION_PORT}/v1/subscribeContext -s -S --header 'Fiware-service: unknownT' --header 'Fiware-servicepath: /' --header 'Content-Type: application/json' --header 'Accept: application/json' -d @- | python -mjson.tool) <<EOF
		{
		    "entities": [
		        {
		            "type": "BloodMeter",
		            "isPattern": "true",
		            "id": ".*"
		        }
		    ],
		    "attributes": [
		        "BloodPressure"
		    ],
		    "reference": "http://${PERSEO_HOST}:${PERSEO_PORT}/notices",
		    "duration": "P1Y",
		    "notifyConditions": [
		        {
		            "type": "ONCHANGE",
		            "condValues": [
		                "BloodPressure"
		            ]
		        }
		    ],
		    "throttling": "PT1S"
		}
		EOF*/
		
	}
	
	public String deleteSubscription(String subscriptionId) {
		String stringURL = "http://" + orion_host+":"+orion_port+"/v2/subscriptions/"+subscriptionId;
		URL url;
		try {
			url = new URL(stringURL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Fiware-service", fiware_service);
            conn.setRequestProperty("Fiware-servicepath", fiware_servicePath);
			conn.setRequestMethod("DELETE");
			conn.setUseCaches (false);
			 StringBuilder sb = new StringBuilder();
		    int responseCode = conn.getResponseCode();
		    BufferedReader br=null;
		    if (200 <= responseCode && responseCode <= 299 ) {
		    	br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
		    	sb.append("Deleted subscription with id "+ subscriptionId);
		    } else
		    	br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));	
	        String line = null;  
	        while ((line = br.readLine()) != null) {  
	        	sb.append(line + "\n");  
	        }
			br.close();
			conn.disconnect();
			return sb.toString();
	
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return "Error Deleting subscription with id "+ subscriptionId;
		} catch (ProtocolException e1) {
			e1.printStackTrace();
			return "Error Deleting subscription with id "+ subscriptionId;
		} catch (IOException e1) {
			e1.printStackTrace();
			return "Error Deleting subscription with id "+ subscriptionId;
		}	
	}
	
	public String deleteRuleAndSubscription(String user_id, String rule_name) {		
		String rule_id = createRuleId(user_id, rule_name);
		
		// Check that rule exists
		if (!existsRule(rule_name, user_id)) return "Rule doesn't exist.";
		
		StringBuilder result = new StringBuilder();
		// delete rule from perseo
		result.append(deleteRuleInPerseo(rule_id));
		
		String subscription_id = ruleDAO.getSubscriptionId(rule_id);
		//delete subscription in Orion
		result.append(deleteSubscription(subscription_id) + "\n");
		
		// If everything OK --> delete from database
		
		int rs = ruleDAO.delete(rule_id, user_id);
		
		result.append("Rows Deleted from DB: "+ rs);
		
		return result.toString();		
	}
	
	public String deleteRuleInPerseo(String rule_id) {
		String stringURL = "http://" + perseo_host+":"+perseo_port+"/rules/"+rule_id;
		URL url;
		try {
			url = new URL(stringURL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
            conn.setRequestProperty("Accept", "application/json");
			conn.setRequestMethod("DELETE");
			conn.setUseCaches (false);
			 StringBuilder sb = new StringBuilder();
		    int responseCode = conn.getResponseCode();
		    BufferedReader br=null;
		    if (200 <= responseCode && responseCode <= 299 ) 
		    	br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
		    else
		    	br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));	
	        String line = null;  
	        while ((line = br.readLine()) != null) {  
	        	sb.append(line + "\n");  
	        }
			br.close();
			conn.disconnect();
			return sb.toString();
	
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return "Error Deleting rule with id "+ rule_id;
		} catch (ProtocolException e1) {
			e1.printStackTrace();
			return "Error Deleting rule with id "+ rule_id;
		} catch (IOException e1) {
			e1.printStackTrace();
			return "Error Deleting rule with id "+ rule_id;
		}	
	}
	
	//send orion subscription
	public String sendSubscription (OrionSubscription subscription) {
		
		Gson gson = new GsonBuilder().serializeNulls().create();
		gson.serializeNulls();
		String jsonSubscription =  gson.toJson(subscription, OrionSubscription.class);
		
		String stringURL = "http://" + orion_host+":"+orion_port+"/v1/subscribeContext";
		
        URL url;
        StringBuilder sb  = new StringBuilder();
        try {
            url = new URL(stringURL);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(15000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Fiware-service", fiware_service);
            conn.setRequestProperty("Fiware-servicepath", fiware_servicePath);
           
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, "UTF-8"));
            
            writer.write(jsonSubscription);
            writer.flush();
            writer.close();
            os.close();

           
          //display what returns the POST request

          sb = new StringBuilder();
          int HttpResult = conn.getResponseCode(); 
          // sb.append(HttpResult + " : " + conn.getResponseMessage());
          System.out.println(sb);
          
          if (HttpResult == HttpURLConnection.HTTP_OK) {
              BufferedReader br = new BufferedReader(
                      new InputStreamReader(conn.getInputStream(), "utf-8"));
              String line = null;  
              while ((line = br.readLine()) != null) {  
                  sb.append(line + "\n");  
              }
              br.close();
              
          } else {
        	  if (conn.getErrorStream()!=null) {
        		  BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
        		  String line = null;  
                  while ((line = br.readLine()) != null) {  
                      sb.append(line + "\n");  
                  }
                  br.close();
        	  }
          }  
        } catch (Exception e) {
            e.printStackTrace();
        } 
        return sb.toString();
	}
}
