package com.xiaoji.duan.aah.operation.logical;

public class LogicalOperationFactory {

	public static LogicalOperation createByName(String op) throws ClassNotFoundException, InstantiationException, IllegalAccessException {

		if (op.startsWith("$")) {
			Class clazz = Class.forName("com.xiaoji.duan.aah.operation.logical." + (new StringBuilder()).append(Character.toUpperCase(op.charAt(1))).append(op.toLowerCase().substring(2)));
			
			return (LogicalOperation) clazz.newInstance();
		} else {
			Class clazz = Class.forName("com.xiaoji.duan.aah.operation.logical." + (new StringBuilder()).append(Character.toUpperCase(op.charAt(0))).append(op.toLowerCase().substring(1)));
			
			return (LogicalOperation) clazz.newInstance();
		}
	}
}
