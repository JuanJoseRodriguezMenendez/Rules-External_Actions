package restAPI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.LinkedTreeMap;

import fiwoo.microservices.rules_External_Actions.fiwoo_rules_External_Actions.Logic;


@RestController
public class PerseoController {

	private static final String SECRET = "_JWT_TOKEN_KEY_";
	
	private static Logic logic;
	
	public PerseoController() {
		logic = new Logic();
	}
	
	// Get Methods
	@RequestMapping(method = RequestMethod.GET, value = "/statements", headers="Accept=application/json")
	public ResponseEntity getRules(@RequestHeader("X-Authorization-s4c") String jwtHeader) throws IllegalArgumentException, UnsupportedEncodingException {
		
		//String jwtHeader="eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.eusFgDqmqIg3_c8buW6ohKCKILHI2Q3ImIoEjSr2Ih42RikUorPy-AntxBrtt82Fc1lnJD9HwF5wnuY76Ezehw";	
		
		String result = logic.getRulesOfUser(decodeUserIdFromJWT(jwtHeader)); 
		return ResponseEntity.status(HttpStatus.OK).body(result);
	}
    
	@RequestMapping(method = RequestMethod.GET, value = "/swagger", headers="Accept=application/json")
	public ResponseEntity  getSwagger() {
		File archivo = null;
	      FileReader fr = null;
	      BufferedReader br = null;
    	  String SwaggerJson="";


	      try {
	         // Apertura del fichero y creacion de BufferedReader para poder
	         // hacer una lectura comoda (disponer del metodo readLine()).
	    	 SwaggerJson="";
	         archivo = new File ("Rules-External_Actions\\src\\main\\resources\\Rules_perseo_Swagger_With_Tokens.json");
	         fr = new FileReader (archivo);
	         br = new BufferedReader(fr);

	         // Lectura del fichero
	         String linea;
	         while((linea=br.readLine())!=null)
	            SwaggerJson=SwaggerJson+"\n"+linea;
	        	 //System.out.println(linea);
	         System.out.println(SwaggerJson);
	      }
	      catch(Exception e){
	         e.printStackTrace();
	      }finally{
	         // En el finally cerramos el fichero, para asegurarnos
	         // que se cierra tanto si todo va bien como si salta 
	         // una excepcion.
	         try{                    
	            if( null != fr ){   
	               fr.close();     
	            }                  
	         }catch (Exception e2){ 
	            e2.printStackTrace();
	         }
	      }
		return ResponseEntity.status(HttpStatus.OK).body(SwaggerJson);
		
	}	
	
	// Post Method
	/*
	 * ENTRY JSON:
	 * { "user_id": "user_id",
	 * 	 "rule" : {rule_JSON} }	* 
	 * 
	 */
	@RequestMapping(value = "/statements/advanced/add", method = RequestMethod.POST, headers="Accept=application/json", consumes = {"application/json"})
	@ResponseBody
	public ResponseEntity addRule(@RequestBody String body, @RequestHeader("X-Authorization-s4c") String jwtHeader) throws IllegalArgumentException, UnsupportedEncodingException {		
		//HMAC
		Algorithm algorithmHS = Algorithm.HMAC512(SECRET);
		
		try {
		    DecodedJWT jwt = JWT.decode(jwtHeader);
		    String subject = jwt.getSubject();
		    System.out.println(subject);
		} catch (JWTDecodeException exception){
		    //Invalid token
		}
		
		Gson gson = new GsonBuilder().serializeNulls().create();
		gson.serializeNulls();
		Object body_aux = gson.fromJson(body, Object.class);
		LinkedTreeMap<Object, Object> body_map = (LinkedTreeMap<Object, Object>) body_aux;
		if (body_map.get("rule") ==  null) 
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\":\"A rule must be sent\"}");
		String ruleJson = gson.toJson(body_map.get("rule"),LinkedTreeMap.class);
//		if (body_map.get("user_id") == null) 
//			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\":\"A user_id must be sent\"}");
//		String user_id =  body_map.get("user_id").toString();
		String description = "no description";
		if (body_map.get("description") != null)
			description = body_map.get("description").toString();
		String response = logic.parseAdvancedRule(ruleJson, decodeUserIdFromJWT(jwtHeader), description);
		System.out.println(response);
		if (response.contains("\"201\":\"created\""))
			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		else
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}
	
	// Delete Methods 
	@RequestMapping(value = "/statements", method = RequestMethod.DELETE, headers= {"Accept=application/json"})
	@ResponseBody
	public ResponseEntity deleteRule(@RequestParam("rule_name") String rule_name, @RequestHeader("X-Authorization-s4c") String jwtHeader) {
		String response = logic.deleteRuleAndSubscription(decodeUserIdFromJWT(jwtHeader), rule_name);
		if (response.contains("\"error\" : \"Rule does not exist\""))
			return new ResponseEntity(response, HttpStatus.BAD_REQUEST);
		else
			return new ResponseEntity(response, HttpStatus.OK);
	}
	
	private String decodeUserIdFromJWT(String jwtHeader) {
		String user_id = "";
		//HMAC
		try {
			Algorithm algorithmHS = Algorithm.HMAC512(SECRET);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
		    DecodedJWT jwt = JWT.decode(jwtHeader);
		    // user is inside the jwt in the sub field
		    String serializedUser = jwt.getSubject();
		    Gson gson = new GsonBuilder().serializeNulls().create();
			gson.serializeNulls();
			Object user = gson.fromJson(serializedUser, Object.class);
			LinkedTreeMap<Object, Object> user_map = (LinkedTreeMap<Object, Object>) user;
			user_id = (String) user_map.get("id");
		} catch (JWTDecodeException exception){
		    //Invalid token
		}
		return jwtHeader;
	}
}
