package com.xiaoji.duan.aah.operation;

import java.util.Map;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.xiaoji.duan.aah.operation.logical.LogicalOperation;
import com.xiaoji.duan.aah.operation.logical.LogicalOperationFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class When {

	private JsonObject def;
	private Object data;
	
	public When(JsonObject def, Object data) {
		this.def = def;
		this.data = data;
	}
	
	public boolean evalate() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		
		Boolean result = Boolean.TRUE;
		
		for (String name : this.def.fieldNames()) {
			if (name.startsWith("d$") && LogicalOperation.isLogicalOperation(name.substring(1))) {
				String realname = name.substring(1);
				LogicalOperation op = LogicalOperationFactory.createByName(realname);
				
				op.setDef(this.def.getValue(name));
				op.setData(this.data);
				
				result = Boolean.logicalAnd(result, op.evalate());
			} else if (name.startsWith("d$.")) {
				String realname = name.substring(1);
				
				Configuration document = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
				
				String json = "{}";
				
				if (this.data instanceof JsonObject) {
					json = ((JsonObject) this.data).encode();
				}
				else if (this.data instanceof JsonArray) {
					json = ((JsonArray) this.data).encode();
				}
				else {
					json = this.data.toString();
				}

				Map<String, Object> val = JsonPath.using(document).parse(json).read(realname);

				When when = new When(this.def.getJsonObject(name), new JsonObject(val));

				result = Boolean.logicalAnd(result, when.evalate());
			} else {
				System.out.println(((JsonObject) this.data).encode());
				Field field = new Field(name, this.def.getJsonObject(name), this.data);
				
				result = Boolean.logicalAnd(result, field.evalate());
			}
		}
		
		return result;
	}
	
	public static void main(String[] args) {
		JsonObject def = new JsonObject()
				.put("d$and", new JsonArray()
						.add(new JsonObject()
								.put("d$.parent.outputs", new JsonObject().put("d$or", new JsonArray()
										.add(new JsonObject()
										.put("who", new JsonObject()
												.put("d$exists", false)))
										.add(new JsonObject()
										.put("when", new JsonObject()
												.put("d$exists", true))
												))))
						.add(new JsonObject()
								.put("d$.parent.outputs", new JsonObject()
										.put("when", new JsonObject()
												.put("d$ne", false)))));
		JsonObject data = new JsonObject().put("name", "testflow").put("parent", new JsonObject().put("outputs", new JsonObject().put("when", true).put("who", "who").put("what", "what")));
		
		When test = new When(def, data);
		
		try {
			System.out.println(test.evalate());
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
