package com.xiaoji.duan.aah.operation;

import com.xiaoji.duan.aah.operation.comparision.ComparisionOperation;
import com.xiaoji.duan.aah.operation.comparision.ComparisionOperationFactory;
import com.xiaoji.duan.aah.operation.query.QueryOperation;
import com.xiaoji.duan.aah.operation.query.QueryOperationFactory;

import io.vertx.core.json.JsonObject;

public class Field {

	private String name;
	private JsonObject def;
	private Object data;
	
	public Field(String name, JsonObject def, Object data) {
		this.name = name;
		this.def = def;
		this.data = data;
	}
	
	public boolean evalate() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		Boolean result = Boolean.TRUE;
		
		for (String name : this.def.fieldNames()) {
			if (name.startsWith("d$") && ComparisionOperation.isComparisionOperation(name.substring(1))) {
				String realname = name.substring(1);
				
				ComparisionOperation op = ComparisionOperationFactory.createByName(realname);
				
				op.setLeft(((JsonObject) this.data).getValue(this.name));
				op.setRight(this.def.getValue(name));
				
				result = Boolean.logicalAnd(result, op.evalate());
			} else if (name.startsWith("d$") && QueryOperation.isQueryOperation(name.substring(1))) {
				String realname = name.substring(1);

				QueryOperation op = QueryOperationFactory.createByName(realname);
				
				System.out.println(this.name + " " + this.data + " " + name);
				op.setData(this.data);
				op.setFieldName(this.name);
				op.setCondition(this.def.getValue(name));
				
				result = Boolean.logicalAnd(result, op.evalate());
			} else {
				System.out.println("Field " + this.name + "'s " + name + " is skipped.");
			}
		}
		
		return result;
	}
}
