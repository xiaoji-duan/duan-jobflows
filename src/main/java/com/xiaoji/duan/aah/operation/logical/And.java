package com.xiaoji.duan.aah.operation.logical;

import com.xiaoji.duan.aah.operation.When;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class And extends LogicalOperation {

	private JsonArray def;
	private Object data;
	
	@Override
	protected boolean eval() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		Boolean result = true;
		
		for (Object val : this.def.getList()) {
			JsonObject anddef = (JsonObject) val;
			
			When when = new When(anddef, this.data);
			result = Boolean.logicalAnd(result, when.evalate());
			
			if (result == false) {
				break;
			}
		}
		
		return result;
	}

	@Override
	public void setDef(Object def) {
		this.def = (JsonArray) def;
	}

	@Override
	public void setData(Object data) {
		this.data = data;
	}

}
