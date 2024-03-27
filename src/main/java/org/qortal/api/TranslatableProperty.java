package org.qortal.api;

interface TranslatableProperty<T> {
	String keyName();
	void setValue(T item, String translation);
	String getValue(T item);
}
