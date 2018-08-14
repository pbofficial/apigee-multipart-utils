# apigee-multipart-utils

## About the Project  

Apigee-multipart-utils is a Java utility which helps with creation and parsing of 'multipart/form-data' in Apigee.

## Problem statement  

In case of a POST request to an API, you must encode the data that forms the body of the request.

HTML forms provide three methods of encoding –
  * application/x-www-form-urlencoded (default)
  * multipart/form-data
  * text/plain

You need to send data in ‘multipart/form-data’ request, if the data being submitted has file content `<input type=”file”>` with some other text components.
In Apigee, there is a seamless support for parsing and creating POST requests with encoding form-urlencoded and text/plain (which can include json or xml content depending upon the Content-Type header). However, when it comes to creating or parsing ‘multipart/form-data’ requests, it is often the case that you end up writing custom code.

Following are two scenarios which may arise with ‘multipart/form-data‘ requests –
  * **Scenario 1**: The back-end expects a ‘multipart/form-data’ request and you need to create such a request from Apigee
  * **Scenario 2**: Any client which sends a request to Apigee, can send a ‘multipart/form-data’ request and you need to parse the request in order to extract or manipulate individual text fields/files

Let’s discuss both these scenarios in detail below.

## Scenario 1  

Let’s say, the client makes an application/json type POST request to Apigee containing Base64 encoded string of the actual pdf file along with other string fields. The Apigee target expects a multipart/form-data request with the raw binary pdf file and remaining text fields.
This means that the json request from client needs to be transformed into ‘multipart/form-data’ which has one file input containing the raw pdf and other text inputs.
To handle this scenario, we've used Mime4J library for payload creation.
[James Mime4J](https://github.com/apache/james-mime4j)
Please have a look at `MultipartParser.createMultipartRequest` method –

```java
if (action.equals(constants.CREATE_METHOD_NAME))
```
Every multipart request requires a boundary, which separates each component (file or text) within the body. You can create this boundary dynamically or keep a constant boundary which is what we did.
Since, the request from client was application/json, we could extract each field separately from JSON in Apigee and send it to the Java code which stores these values in String variables.
The next challenge is converting the Base64 file into a raw pdf. This is a bit tricky considering that a raw document is binary in nature, which implies once the conversion is completed, we can not deal with string.

> The final output is a ‘multipart/form-data’ request, sent as a Java InputStream to Apigee,
> which Apigee then send to the target.

**Note**- If there is a requirement to create ‘multipart/form-data’ request without a raw file, you need not write a Java code. The following article can be helpful to fulfil that requirement using JavaScript, [Apigee community](https://community.apigee.com/questions/25630/need-to-send-a-request-to-a-service-with-contentty.html)

## Scenario 2  

Let’s say, the client makes a ‘multipart/form-data’ request which contains a raw file along with other text components. We need to perform few transformations on the text components without hampering the binary raw file and forward it to the Apigee target.
Again we're using [Mime4J](https://github.com/apache/james-mime4j) library to deal with ‘multipart/form-data’ 

Please have a look at `MultipartParser.parseMultipartRequest` method-

```java
if (action.equals(constants.PARSE_METHOD_NAME))
```
Here, the client is already making a ‘multipart/form-data’ request. In Java code, we can access the Apigee *request object* directly using Apigee jars (message-flow and expressions). The request contains a binary raw file and hence, we made sure not to deal with String as it could damage the file. Instead, we used mime type to check if the part is text/plain and only then deal with String. In all other cases, we used binary streams.

Once all text manipulations are finished, we create our own multipart data payload and send that as an InputStream to the Apigee target server. We're also setting debug variables wherever applicable in the Java code for easy debugging.

**Note** – In case you are not dealing with files in your multipart request and want to extract or perform simple text manipulations, then you can refer the following article and use JavaScript instead: [Apigee community](https://community.apigee.com/questions/36743/how-to-extract-multipartform-data-from-post-reques.html)

> The Java code can be customized as per your requirement.
> If you need help in sending multipart/form-data requests from POSTMAN, refer this post - [Stack Overflow](https://stackoverflow.com/questions/16015548/tool-for-sending-multipart-form-data-request)

## Detailed documentation on the description and usage  

Refer [README.docx](https://github.com/pbofficial/apigee-multipart-utils/blob/master/README.docx)

## Contributors  

The project is developed by [Rampradeep K](https://github.com/rampradeepk) and [Pritam Bhowmik](https://github.com/pbofficial) with major contributions from [Aashish Pathak](https://github.com/aashish-pathak) and [Ketaki Pandit](https://github.com/ketakipandit26). This project has been open-sourced by [Fresh Gravity](http://www.freshgravity.com/) under MIT license.