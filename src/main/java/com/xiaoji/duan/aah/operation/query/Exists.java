package com.xiaoji.duan.aah.operation.query;

import io.vertx.core.json.JsonObject;

public class Exists extends QueryOperation {

	private JsonObject data;
	private String fieldname;
	private Boolean cond;
	
	@Override
	public void setData(Object data) {
		this.data = (JsonObject) data;
		
	}

	@Override
	public void setCondition(Object cond) {
		this.cond = (Boolean) cond;
	}

	@Override
	protected boolean eval() {
		return !Boolean.logicalXor(this.data.containsKey(fieldname), this.cond); 
	}

	@Override
	public void setFieldName(String fieldname) {
		this.fieldname = fieldname;
	}

}
