/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.synapse.util.xpath;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.ParentAware;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapsePropertiesLoader;
import org.apache.synapse.config.xml.SynapsePath;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.jaxen.JaxenException;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.internal.PathTokenizer;

public class SynapseJsonPath extends SynapsePath {

    private static final Log log = LogFactory.getLog(SynapseJsonPath.class);

    private String enableStreamingJsonPath = SynapsePropertiesLoader.loadSynapseProperties().
    getProperty(SynapseConstants.STREAMING_JSONPATH_PROCESSING);

    private JsonPath jsonPath;

    private boolean isWholeBody = false;

    public SynapseJsonPath(String jsonPathExpression)  throws JaxenException {
        super(jsonPathExpression, SynapsePath.JSON_PATH, log);
        this.contentAware = true;
        this.expression = jsonPathExpression;
        jsonPath = JsonPath.compile(jsonPathExpression);
        // Check if the JSON path expression evaluates to the whole payload. If so no point in evaluating the path.
        if ("$".equals(jsonPath.getPath().trim()) || "$.".equals(jsonPath.getPath().trim())) {
            isWholeBody = true;
        }
        this.setPathType(SynapsePath.JSON_PATH);
    }

    public String stringValueOf(final String jsonString) {
        if (jsonString == null) {
            return "";
        }
        if (isWholeBody) {
            return jsonString;
        }
        Object read;
        read = jsonPath.read(jsonString);
        return (null == read ? "null" : read.toString());
    }

    public String stringValueOf(MessageContext synCtx) {
        org.apache.axis2.context.MessageContext amc = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
        InputStream stream;
        if (!JsonUtil.hasAJsonPayload(amc) || "true".equals(enableStreamingJsonPath)) {
            try {
                if (null == amc.getEnvelope().getBody().getFirstElement()) {
                    // Get message from PT Pipe.
                    stream = getMessageInputStreamPT(amc);
                    if (stream == null) {
                        stream = JsonUtil.getJsonPayload(amc);
                    } else {
                        JsonUtil.newJsonPayload(amc, stream, true, true);
                    }
                } else {
                    // Message Already built.
                    stream = JsonUtil.toJsonStream(amc.getEnvelope().getBody().getFirstElement());
                }
                return stringValueOf(stream);
            } catch (IOException e) {
                handleException("Could not find JSON Stream in PassThrough Pipe during JSON path evaluation.", e);
            }
        } else {
            stream = JsonUtil.getJsonPayload(amc);
            return stringValueOf(stream);
        }
        return "";
    }

    public String stringValueOf(final InputStream jsonStream) {
        if (jsonStream == null) {
            return "";
        }
        if (isWholeBody) {
            try {
                return IOUtils.toString(jsonStream);
            } catch(IOException e) {
                log.error("#stringValueOf. Could not convert JSON input stream to String.");
                return "";
            }
        }
        Object read;
        try {
            read = jsonPath.read(jsonStream);
            if (log.isDebugEnabled()) {
                log.debug("#stringValueOf. Evaluated JSON path <" + jsonPath.getPath() + "> : <" + (read == null ? null : read.toString()) + ">");
            }
            return (null == read ? "null" : read.toString());
        } catch (IOException e) {
            handleException("Error evaluating JSON Path <" + jsonPath.getPath() + ">", e);
        } catch (Exception e) { // catch invalid json paths that do not match with the existing JSON payload.
            log.error("#stringValueOf. Error evaluating JSON Path <" + jsonPath.getPath() + ">. Returning empty result. Error>>> " + e.getLocalizedMessage());
            return "";
        }
        if (log.isDebugEnabled()) {
            log.debug("#stringValueOf. Evaluated JSON path <" + jsonPath.getPath() + "> : <null>.");
        }
        return "";
    }

    public String getJsonPathExpression() {
        return expression;
    }

    public void setJsonPathExpression(String jsonPathExpression) {
        this.expression = jsonPathExpression;
    }
    
    /**
     * Read the JSON Stream and returns a list of string representations of return JSON elements from JSON path.
     */
	@Override
	public Object evaluate(Object object) throws JaxenException {
		List result = null;
		MessageContext synCtx = null;
		if (object != null && object instanceof MessageContext) {
			synCtx = (MessageContext) object;
			result = listValueOf(synCtx);
		}
		if (result == null)
			result = new ArrayList();
		return result;
	}
    
    /* 
     * Read JSON stream and return and object
     */
	public List listValueOf(MessageContext synCtx) {
		org.apache.axis2.context.MessageContext amc = ((Axis2MessageContext) synCtx).getAxis2MessageContext();
		InputStream stream;
		if (!JsonUtil.hasAJsonPayload(amc) || "true".equals(enableStreamingJsonPath)) {
			try {
				if (null == amc.getEnvelope().getBody().getFirstElement()) {
					// Get message from PT Pipe.
					stream = getMessageInputStreamPT(amc);
					if (stream == null) {
						stream = JsonUtil.getJsonPayload(amc);
					} else {
						JsonUtil.newJsonPayload(amc, stream, true, true);
					}
				} else {
					// Message Already built.
					stream = JsonUtil.toJsonStream(amc.getEnvelope().getBody().getFirstElement());
				}
				return listValueOf(stream);
			} catch (IOException e) {
				handleException("Could not find JSON Stream in PassThrough Pipe during JSON path evaluation.",
				                e);
			}
		} else {
			stream = JsonUtil.getJsonPayload(amc);
			return listValueOf(stream);
		}
		return null;

	}
	
	public List listValueOf(final InputStream jsonStream) {
        if (jsonStream == null) {
            return null;
        }
        List result=new ArrayList();
        Object object;
        try {
        	object = jsonPath.read(jsonStream);
            if (log.isDebugEnabled()) {
                log.debug("#listValueOf. Evaluated JSON path <" + jsonPath.getPath() + "> : <" + (object == null ? null : object.toString()) + ">");
            }
            
            if(object !=null){
            	if(object instanceof JSONArray){
                	JSONArray arr = (JSONArray)object;
                	for (Object obj : arr) {
                		result.add(obj!=null?obj:"null");
                    }
            	}else if(object instanceof JSONObject){
            		result.add(object);
            	}
            }
        } catch (IOException e) {
            handleException("Error evaluating JSON Path <" + jsonPath.getPath() + ">", e);
        } catch (Exception e) { // catch invalid json paths that do not match with the existing JSON payload.
            log.error("#listValueOf. Error evaluating JSON Path <" + jsonPath.getPath() + ">. Returning empty result. Error>>> " + e.getLocalizedMessage());
        }
        if (log.isDebugEnabled()) {
            log.debug("#listValueOf. Evaluated JSON path <" + jsonPath.getPath() + "> : <null>.");
        }
        return result;
    }
	
	public Object findParent(Object rootObject){
		PathTokenizer tokenizer=new PathTokenizer(jsonPath.getPath());
		tokenizer.removeLastPathToken();
		StringBuilder sb=new StringBuilder();
		List<String> fragments=tokenizer.getFragments();
		for(int i=0;i<fragments.size();i++){
			sb.append(fragments.get(i));
			if(i<fragments.size()-1)
				sb.append(".");
		}
		JsonPath tempPath=JsonPath.compile(sb.toString());
		return tempPath.find(rootObject);
	}
	
	// TODO Jsonpath should expose internal package to OSGI. because I'm using pathtokenizer here
	// TODO Json smart should me modified. Add ParentAware again
	public Object append(Object rootObject, Object parent, Object child){
		if(rootObject!=null && rootObject.equals(parent)){
			JSONArray array=new JSONArray();
			array.add(rootObject);
			rootObject = array;
			parent=array;
		}
		if(parent !=null && parent instanceof JSONArray){
			((JSONArray)parent).add(child);
		}else if(parent!=null && parent instanceof JSONObject){
			ParentAware newParent=((JSONObject)parent).getParent();
			if(newParent!=null && newParent instanceof JSONObject){
				JSONObject obj=(JSONObject)newParent;
				for(String key:obj.keySet()){
					if(obj.get(key).equals(parent)){
						JSONArray array=new JSONArray();
						array.add(obj.get(key));
						array.add(child);
						obj.put(key, array);
						break;
					}
				}
			}
		}
		return rootObject;
	}
}
