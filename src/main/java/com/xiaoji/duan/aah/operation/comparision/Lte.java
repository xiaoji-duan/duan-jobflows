package com.xiaoji.duan.aah.operation.comparision;

public class Lte extends ComparisionOperation {

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
			if (left instanceof Number && right instanceof Number) {
				return ((Number) left).doubleValue() <= ((Number) right).doubleValue();
			} else if (left instanceof Number && right instanceof String) {
				return ((Number) left).doubleValue() <= Double.valueOf((String) right);
			} else if (left instanceof String && right instanceof Number) {
				return Double.valueOf((String) left) <= ((Number) right).doubleValue();
			} else if (left instanceof String && right instanceof String) {
				return ((String) left).compareTo((String) right) <= 0;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

}
