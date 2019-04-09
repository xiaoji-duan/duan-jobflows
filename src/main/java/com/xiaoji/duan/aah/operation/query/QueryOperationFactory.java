package com.xiaoji.duan.aah.operation.query;

public class QueryOperationFactory {

	public static QueryOperation createByName(String op) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		if (op.startsWith("$")) {
			Class clazz = Class.forName("com.xiaoji.duan.aah.operation.query." + (new StringBuilder()).append(Character.toUpperCase(op.charAt(1))).append(op.toLowerCase().substring(2)));
			
			return (QueryOperation) clazz.newInstance();
		} else {
			Class clazz = Class.forName("com.xiaoji.duan.aah.operation.query." + (new StringBuilder()).append(Character.toUpperCase(op.charAt(0))).append(op.toLowerCase().substring(1)));
			
			return (QueryOperation) clazz.newInstance();
		}
	}
}
