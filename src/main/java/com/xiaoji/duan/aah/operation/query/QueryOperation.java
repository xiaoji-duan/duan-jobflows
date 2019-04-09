package com.xiaoji.duan.aah.operation.query;

public abstract class QueryOperation {

	public static boolean isQueryOperation(String op) {
		return "$exists".contains(op.toLowerCase());
	}
	
	abstract public void setData(Object data);
	abstract public void setFieldName(String fieldname);
	abstract public void setCondition(Object cond);
	abstract protected boolean eval();
	
	public boolean evalate() {
		return eval();
	}
}
