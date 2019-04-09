package com.xiaoji.duan.aah.operation.comparision;

public abstract class ComparisionOperation {

	public static boolean isComparisionOperation(String op) {
		return "$eq|$gt|$gte|$in|$lt|$lte|$ne|$nin".contains(op.toLowerCase());
	}
	
	abstract public void setLeft(Object left);
	abstract public void setRight(Object right);
	abstract protected boolean eval();
	
	public boolean evalate() {
		return eval();
	}
}
