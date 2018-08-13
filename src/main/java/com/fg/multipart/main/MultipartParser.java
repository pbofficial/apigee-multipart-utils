package com.fg.multipart.main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.xml.bind.DatatypeConverter;

import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.field.DefaultFieldParser;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.message.MessageImpl;
import org.apache.james.mime4j.message.MultipartImpl;
import org.apache.james.mime4j.storage.DefaultStorageProvider;
import org.apache.james.mime4j.storage.MemoryStorageProvider;
import org.apache.james.mime4j.stream.Field;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.MessageContext;
import com.fg.multipart.constants.MultipartConstants;
import com.fg.multipart.parser.ReplacingInputStream;

@SuppressWarnings("restriction")
public class MultipartParser implements Execution {

	private MessageContext msgContext;
	@SuppressWarnings("unused")
	private ExecutionContext execContext;
	private Map<String, String> properties;
	private static String action;
	private static String vendorId;
	private static String publicKeyStr;
	private static String base64File;
	private static String boundary;
	private static String serverTransactionId;
	private static String password;
	private static String fileName;
	private static String contentType;
	private static Message mimeMessage;
	private static Multipart multipart;
	private static MultipartConstants constants;
	private static String boundaryContentType = null;

	public MultipartParser(Map<String, String> properties) {
		this.properties = properties;
	}

	public MultipartParser() {
		// No-arg constructor
	}

	public ExecutionResult execute(MessageContext msgContext, ExecutionContext execContext) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		InputStream contentInputStream = null, returnInputStream = null, ris = null;
		
		msgContext.setVariable("CONSTANTS_INITIALIZED", true);
		constants = new MultipartConstants();

		// Initialize variables
		init(msgContext, execContext);

		// Set default storage to in-memory instead of file system
		DefaultStorageProvider.setInstance(new MemoryStorageProvider());

		// Create multipart message method
		if (action.equals(constants.CREATE_METHOD_NAME)) {
			try {

				// Set the boundary from Apigee
				boundaryContentType = constants.CONTENT_TYPE + boundary;

				/*
				 * Start MIME message creation
				 */

				mimeMessage = new MessageImpl();
				multipart = new MultipartImpl(constants.MESSAGE_INSTANCE);

				/*
				 * Add content-type to the entire MIME message
				 */

				Header messageHeader = new HeaderImpl();
				messageHeader.addField(DefaultFieldParser.parse(boundaryContentType));
				mimeMessage.setHeader(messageHeader);
				msgContext.setVariable("MESSAGE_3", "CONTENT TYPE SET");
				
				/*
				 * Add parts to MIME message; add ServerTransactionId
				 */

				BodyPart messageBodyPart = BodyPartBuilder.create()
						.setBody(BasicBodyFactory.INSTANCE.textBody(serverTransactionId)).build();
				Header header = new HeaderImpl();
				Map<String, String> hashMap = new HashMap();
				hashMap.put(constants.CONTENT_DISPOSITION_NAME_KEY, constants.SERVER_TRANSACTION_ID_VARIABLE);
				header.setField(Fields.contentDisposition(constants.CONTENT_DISPOSITION_FORM_DATA_KEY, hashMap));
				messageBodyPart.setHeader(header);

				// Add Server TransactionId body-part to multipart
				multipart.addBodyPart(messageBodyPart);
				msgContext.setVariable("MESSAGE_4", "SERVER TRANSACTION ID SET");

				/*
				 * Add parts to MIME message; add VendorId
				 */

				BodyPart vendorIdBodyPart = BodyPartBuilder.create()
						.setBody(BasicBodyFactory.INSTANCE.textBody(vendorId)).build();
				Header vendorIdheader = new HeaderImpl();
				Map<String, String> vendorIdhashMap = new HashMap();
				vendorIdhashMap.put(constants.CONTENT_DISPOSITION_NAME_KEY, constants.VENDOR_ID_VARIABLE);
				vendorIdheader.setField(
						Fields.contentDisposition(constants.CONTENT_DISPOSITION_FORM_DATA_KEY, vendorIdhashMap));
				vendorIdBodyPart.setHeader(vendorIdheader);

				// Add VendorId body-part to multipart
				multipart.addBodyPart(vendorIdBodyPart);
				msgContext.setVariable("MESSAGE_5", "VENDOR ID SET");

				/*
				 * Add parts to MIME message; encrypt and add password
				 */

				// Check if password exists
				if (!(checkIfNullOrEmpty(password))) {

					// Encrypt password
					String encryptedPassword = encryptPassword(password);

					// Add encrypted document Password
					messageBodyPart = BodyPartBuilder.create()
							.setBody(BasicBodyFactory.INSTANCE.textBody(encryptedPassword)).build();
					header = new HeaderImpl();
					hashMap = new HashMap();
					hashMap.put(constants.CONTENT_DISPOSITION_NAME_KEY, constants.PASSWORD_VARIABLE);
					header.setField(Fields.contentDisposition(constants.CONTENT_DISPOSITION_FORM_DATA_KEY, hashMap));
					messageBodyPart.setHeader(header);

					// Add password body-part to multipart
					multipart.addBodyPart(messageBodyPart);
					msgContext.setVariable("MESSAGE_6", "PASSWORD SET");
				}

				/*
				 * Add parts to MIME message; add file content (decode Base64 file string to binary)
				 */
				
				byte[] base64DecodedFile = Base64.getDecoder().decode(base64File.getBytes());
				messageBodyPart = BodyPartBuilder.create()
						.setBody(BasicBodyFactory.INSTANCE.binaryBody(base64DecodedFile)).build();
				
				msgContext.setVariable("MESSAGE_7", "FILE CONTENT DECODED SUCCESSFULLY");

				header = new HeaderImpl();
				hashMap = new LinkedHashMap();
				hashMap.put(constants.CONTENT_DISPOSITION_NAME_KEY, constants.CONTENT_DISPOSITION_FILE_VALUE);
				hashMap.put(constants.FILENAME_VARIABLE, fileName);
				header.setField(Fields.contentDisposition(constants.CONTENT_DISPOSITION_FORM_DATA_KEY, hashMap));
				header.setField(Fields.contentType(contentType));
				messageBodyPart.setHeader(header);

				// Add file body-part to multipart
				multipart.addBodyPart(messageBodyPart);
				mimeMessage.setBody(multipart);

				msgContext.setVariable("MESSAGE_7", "FILE SET");
				
				/*
				 * Create final MIME message stream
				 */
				byte[] messageBytes = DefaultMessageWriter.asBytes(mimeMessage);

				contentInputStream = new ByteArrayInputStream(messageBytes);
				
				/*
				 * Remove Content-Type multipart boundary from the request content before sending to Apigee
				 */
				contentInputStream = new ReplacingInputStream(contentInputStream, boundaryContentType,
						constants.EMPTY_STRING);

				int byteCount;
				while (-1 != (byteCount = contentInputStream.read()))
					outputStream.write(byteCount);

				contentInputStream = new ByteArrayInputStream(outputStream.toByteArray());

				/*
				 * Set content to Apigee request body
				 */
				msgContext.getMessage().setContent(contentInputStream);
				msgContext.setVariable("MESSAGE_8", "MESSAGE CREATION_SUCCESSFUL");
				
				/*
				 * Return response to Apigee
				 */
				return ExecutionResult.SUCCESS;
			} catch (Exception e) {
				msgContext.setVariable("ERROR_MESSAGE", e.getMessage());
				return ExecutionResult.ABORT;
			} finally {
				try {
					contentInputStream.close();
					outputStream.close();
				} catch (Exception e) {
					msgContext.setVariable("FINALLY_ERROR_MESSAGE", e.getMessage());
					return ExecutionResult.ABORT;
				}
			}
		}
		
		// Parse multipart message method
		if (action.equals(constants.PARSE_METHOD_NAME)) {
		try {
			
			// Perform Multi-part parsing
			Message mimeMessage = parseMultipartStream();
			msgContext.setVariable("SIZE_RETURN", ((Multipart) mimeMessage.getBody()).getCount());

			if (mimeMessage != null) {

				byte[] messageBytes = DefaultMessageWriter.asBytes(mimeMessage);

				msgContext.setVariable("MESSAGE_16", "Written to OutputStream");
				returnInputStream = new ByteArrayInputStream(messageBytes);

				
				ris = new ReplacingInputStream(returnInputStream, boundaryContentType, "");

				

				int byteCount;
				while (-1 != (byteCount = ris.read()))
					outputStream.write(byteCount);

				returnInputStream = new ByteArrayInputStream(outputStream.toByteArray());
				

				msgContext.setVariable("MESSAGE_17", "Converted to InputStream");

				msgContext.setVariable("APIGEE_INPUT", returnInputStream);

				// returnInputStream = new FilterHeaderTrailerInputStream(returnInputStream);
				msgContext.getMessage().setContent(returnInputStream);
				msgContext.setVariable("MESSAGE_18", "Set InputStream and return to Apigee");
				return ExecutionResult.SUCCESS;
			}
		} catch (Exception e) {
			msgContext.setVariable("ERROR_MESSAGE", e.getMessage());
			return ExecutionResult.ABORT;
		} finally {
			try {
				returnInputStream.close();
				outputStream.close();
			} catch (IOException e) {
				msgContext.setVariable("FINALLY_ERROR_MESSAGE", e.getMessage());
			}
		}
	}

		return ExecutionResult.SUCCESS;
	}

	/*
	 * Check if multipart exists and then parse the message
	 */
	private Message parseMultipartStream() throws Exception {

		msgContext.setVariable("MESSAGE_3", "In parseMultipartStream()");

		// Get content type from request header
		String contentType = msgContext.getMessage().getHeader("Content-Type");
		String contentTypeString = "Content-Type: " + contentType + "\n\n";
		msgContext.setVariable("MESSAGE_4", contentTypeString);

		// Convert content-type to inputstream
		InputStream contentTypeStream = new ByteArrayInputStream(contentTypeString.getBytes());

		// Convert request.content from Apigee to inputstream
		InputStream requestContentStream = msgContext.getMessage().getContentAsStream();

		// Sequence content-type and request.content
		InputStream multipartStream = new SequenceInputStream(contentTypeStream, requestContentStream);
		msgContext.setVariable("MESSAGE_5", "Input Streams merged");

		// Use the merged inputstream to created a MIME message
		MessageBuilder builder = new DefaultMessageBuilder();
		mimeMessage = builder.parseMessage(multipartStream);

		msgContext.setVariable("MESSAGE_6", "Mime message created");

		Field contentTypeField = mimeMessage.getHeader().getField("Content-Type");
		msgContext.setVariable("MESSAGE_7", "Content Type extracted from MIME message");
		msgContext.setVariable("MESSAGE_8", contentTypeField);

		// Create a boundary which needs to be the first line in the response back to Apigee
		boundaryContentType = contentTypeField.getName().trim() + ": " + contentTypeField.getBody().trim();

		// Process only-if the request.content is multi-part
		if (mimeMessage.isMultipart()) {
			msgContext.setVariable("SIZE_BEFORE", ((Multipart) mimeMessage.getBody()).getCount());
			msgContext.setVariable("MESSAGE_9", "Multi-part message detected");
			multipart = (Multipart) mimeMessage.getBody();

			// Parse body-parts
			parseBodyParts();

			// Add a vendorId body-part from KVM
			BodyPart messageBodyPart = BodyPartBuilder.create().setBody(BasicBodyFactory.INSTANCE.textBody(vendorId))
					.build();
			Header header = new HeaderImpl();
			Map<String, String> hashMap = new HashMap();
			hashMap.put("name", "vendorId");
			
			header.setField(Fields.contentDisposition("form-data", hashMap));
			messageBodyPart.setHeader(header);

			// Add body-part to multipart
			multipart.addBodyPart(messageBodyPart);

			msgContext.setVariable("SIZE_AFTER", ((Multipart) mimeMessage.getBody()).getCount());
		
			return mimeMessage;
		} else {
			// Ignore if message not multi-part
			msgContext.setVariable("MESSAGE_12", "MIME Message not multi-part");
			return null;
		}
	}

	/*
	 * Parse each body part as needed
	 */
	private Multipart parseBodyParts() throws Exception {
		msgContext.setVariable("MESSAGE_10", "In parseBodyParts()");
		for (Entity part : multipart.getBodyParts()) {
			msgContext.setVariable(part.getDispositionType(), part.getBody().toString());
			
			// Encrypt only-if the part is password
			if (part.getMimeType().contains("text/plain")) {
				msgContext.setVariable("MESSAGE_11", "text/plain MIME detected");
				Field header = part.getHeader().getField("Content-Disposition");
				if (header.getBody().contains("password")) {
					msgContext.setVariable("MESSAGE_12", "Password field found");

					// Get password value
					String losFilePassword = getTxtPart(part);

					msgContext.setVariable("MESSAGE_13", "Password: " + losFilePassword);

					// Encrypt password using public key
					String encryptedPassword = encryptPassword(losFilePassword);
					msgContext.setVariable("MESSAGE_14", "Encrypted Password: " + encryptedPassword);
					TextBody textBody = new BasicBodyFactory().textBody(encryptedPassword);

					// Remove the previous password
					part.removeBody();

					// Set the encrypted password
					part.setBody(textBody);
					msgContext.setVariable("MESSAGE_15", "Encrypted password set in Multipart");
				}
			}
		}
		return multipart;
	}

	/*
	 * Get text content from a body part
	 */
	private String getTxtPart(Entity part) throws IOException {
		// Return text as outputstream
		TextBody tb = (TextBody) part.getBody();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		tb.writeTo(baos);

		// Convert outputstream to string
		return new String(baos.toByteArray());
	}
	
	/*
	 *  Initialize flow variables from Apigee (Java callout properties)
	 */
	
	private void init(MessageContext msgContext, ExecutionContext execContext) {
		msgContext.setVariable("MESSAGE_1", "In init()");
		this.msgContext = msgContext;
		this.execContext = execContext;

		action = resolveVariable((String) properties.get(constants.ACTION_VARIABLE));
		publicKeyStr = resolveVariable((String) properties.get(constants.PUBLIC_KEY_VARIABLE));
		vendorId = resolveVariable((String) properties.get(constants.VENDOR_ID_VARIABLE));
		base64File = resolveVariable((String) properties.get(constants.BASE64_FILE_VARIABLE));
		serverTransactionId = resolveVariable((String) properties.get(constants.SERVER_TRANSACTION_ID_VARIABLE));
		password = resolveVariable((String) properties.get(constants.PASSWORD_VARIABLE));
		fileName = resolveVariable((String) properties.get(constants.FILENAME_VARIABLE));
		contentType = resolveVariable((String) properties.get(constants.CONTENT_TYPE_VARIABLE));
		boundary = resolveVariable((String) properties.get(constants.BOUNDARY_VARIABLE));

		msgContext.setVariable("ACTION", action);
		msgContext.setVariable("PUBLIC_KEY", publicKeyStr);
		msgContext.setVariable("VENDOR_ID", vendorId);
		
		if(checkIfNullOrEmpty(base64File));
		else
			msgContext.setVariable("BASE64_FILE_LENGTH", base64File.length());
		
		msgContext.setVariable("SERVER_TRANSACTION_ID", serverTransactionId);
		msgContext.setVariable("PASSWORD", password);
		msgContext.setVariable("FILE_NAME", fileName);
		msgContext.setVariable("CONTENT_TYPE", contentType);
		msgContext.setVariable("BOUNDARY", boundary);
		msgContext.setVariable("MESSAGE_2", "init() finished)");
	}

	/*
	 * Get variable values from Apigee
	 */
	private String resolveVariable(String variable) {
		if (isEmpty(variable)) {
			return variable;
		}
		if ((variable.startsWith("{")) && (variable.endsWith("}"))) {
			String value = (String) msgContext.getVariable(variable.substring(1, variable.length() - 1));
			if (isEmpty(value)) {
				return variable;
			}
			return value;
		}

		return variable;
	}
	
	/*
	 * Check if an Apigee variable value is null or empty
	 */
	private boolean isEmpty(String variable) {
		return (variable == null) || (variable.length() == 0);
	}

	private String encryptPassword(String password) throws Exception {
		return encrypt(password, constants.ENCRYPTION_ALGO_LONG, buildPublicKey(publicKeyStr));
	}

	/*
	 * Return public key
	 */
	private static PublicKey buildPublicKey(String publicKeyStr) throws Exception {
		publicKeyStr = publicKeyStr.replace(constants.BEGIN_PUBLIC_KEY, constants.EMPTY_STRING)
				.replace(constants.END_PUBLIC_KEY, constants.EMPTY_STRING).replaceAll("\\s", constants.EMPTY_STRING);

		byte[] publicKeyDER = java.util.Base64.getDecoder().decode(publicKeyStr);

		KeyFactory keyFactory = KeyFactory.getInstance(constants.ENCRYPTION_ALGO_SHORT);
		PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyDER));
		return publicKey;
	}

	/*
	 * Encrypt string using the encryption algorithm specified
	 */
	private static String encrypt(String raw, String encryptAlgo, Key k) {
		String strEncrypted = constants.EMPTY_STRING;
		try {
			Cipher cipher = Cipher.getInstance(encryptAlgo);
			cipher.init(1, k);
			byte[] encrypted = cipher.doFinal(raw.getBytes(constants.CHARSET));
			strEncrypted = DatatypeConverter.printHexBinary(encrypted).toLowerCase();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return strEncrypted;
	}

	/*
	 * Check is a string has valid value
	 */
	private static boolean checkIfNullOrEmpty(String str) {
		if ((str == null) || (str == "") || (str.length() < 1))
			return true;
		return false;
	}
}