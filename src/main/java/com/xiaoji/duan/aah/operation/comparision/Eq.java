package com.xiaoji.duan.aah.operation.comparision;

public class Eq extends ComparisionOperation {

	private Object left;
	private Object right;
	
	@Override
	public void setLeft(Object left) {
		this.left = left;
	}

	@Override
	public void setRight(Object right) {
		this.right = right;
	}

	@Override
	protected boolean eval() {
		if (left != null && right != null) {
			return left.equals(right);
		} else {
			return left == right;
		}
	}

}
