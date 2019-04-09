package com.xiaoji.duan.aah.operation.logical;

import io.vertx.core.json.JsonObject;

public abstract class LogicalOperation {

	public static boolean isLogicalOperation(String op) {
		return "$and|$or|$not".contains(op.toLowerCase());
	}
	
	abstract protected boolean eval() throws ClassNotFoundException, InstantiationException, IllegalAccessException;
	abstract public void setDef(Object def);
	abstract public void setData(Object data);
	
	public boolean evalate() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		return eval();
	}
}
