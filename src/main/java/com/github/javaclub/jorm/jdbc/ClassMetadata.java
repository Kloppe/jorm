/*
 * @(#)ClassMetadata.java	2011-7-20
 *
 * Copyright (c) 2011. All Rights Reserved.
 *
 */

package com.github.javaclub.jorm.jdbc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.github.javaclub.jorm.JormException;
import com.github.javaclub.jorm.annotation.Basic;
import com.github.javaclub.jorm.annotation.Entity;
import com.github.javaclub.jorm.annotation.Id;
import com.github.javaclub.jorm.annotation.ManyToMany;
import com.github.javaclub.jorm.annotation.ManyToOne;
import com.github.javaclub.jorm.annotation.NoColumn;
import com.github.javaclub.jorm.annotation.OneToMany;
import com.github.javaclub.jorm.annotation.OneToOne;
import com.github.javaclub.jorm.annotation.PK;
import com.github.javaclub.jorm.annotation.constant.FetchType;
import com.github.javaclub.jorm.annotation.constant.GenerationType;
import com.github.javaclub.jorm.common.Annotations;
import com.github.javaclub.jorm.common.MethodType;
import com.github.javaclub.jorm.common.Reflections;
import com.github.javaclub.jorm.common.Strings;
import com.github.javaclub.jorm.jdbc.process.DummyFieldProcessor;
import com.github.javaclub.jorm.jdbc.process.FieldProcessor;
import com.github.javaclub.jorm.jdbc.sql.AnnotationModelHelper;
import com.github.javaclub.jorm.jdbc.sql.SqlParams;

/**
 * ClassMetadata
 *
 * @author <a href="mailto:gerald.chen.hz@gmail.com">Gerald Chen</a>
 * @version $Id: ClassMetadata.java 2011-7-20 上午11:39:56 Exp $
 */
public class ClassMetadata {
	
	private static ConcurrentMap<Class<?>, ClassMetadata> metadatas = new ConcurrentHashMap<Class<?>, ClassMetadata>();

	public Class<?> clazz;
	public String tableName;
	public boolean hasLazyField = false;
	public boolean isLazyEntity = false;
	
	public Field identifierField;
	public Method getIdentifierMethod;
	public Method setIdentifierMethod;
	
	public List<Field> definedPkFields = new ArrayList<Field>();
	public List<Field> definedIdFields = new ArrayList<Field>();
	public List<Field> generatedFields = new ArrayList<Field>();
	public List<Field> insertFields = new ArrayList<Field>();
	public List<Field> updateFields = new ArrayList<Field>();
	public List<Field> lazyFields = new ArrayList<Field>();
	public List<Field> allFields = new ArrayList<Field>();
	
	public List<Field> OneToOneFields = new ArrayList<Field>();
	public List<Field> OneToManyFields = new ArrayList<Field>();
	public List<Field> ManyToOneFields = new ArrayList<Field>();
	public List<Field> ManyToManyFields = new ArrayList<Field>();
	
	public String insert;
	
	private Map<String, String> fieldColumns = new HashMap<String, String>();
	
	private ClassMetadata(Class<?> clazz) {
		//System.out.println("ClassMetadata constructor [" + clazz.getName() +  "] initialized ...");
		this.clazz = clazz;
		String pkFieldNamesJoin = processClassAnnotation(clazz);
		processFieldAnnotation(clazz, pkFieldNamesJoin);
	}

	public boolean isIdField(Field field) {
		return field.getAnnotation(Id.class) != null;
	}
	
	public boolean isEntityLazy() {
		return hasLazyField || isLazyEntity;
	}
	
	public boolean isJustFieldLazy() {
		return hasLazyField && (!isLazyEntity);
	}
	
	public boolean isIdentityfierAutoGenerated() {
		if(null == this.identifierField) {
			return false;
		}
		Id ann = this.identifierField.getAnnotation(Id.class);
		return (null != ann && GenerationType.isAutoGenerated(ann.value()));
	}
	
	public boolean hasAssociated() {
		return (OneToOneFields.size() > 0 || OneToManyFields.size() > 0 
				|| ManyToManyFields.size() > 0 || ManyToOneFields.size() > 0);
	}
	
	/**
	 * Tests the identifier field's generation strategy is <code>GenerationType.FOREIGN</code>.
	 *
	 * @return <code>true</code> if the identifier field's generation strategy is <code>GenerationType.FOREIGN</code>, 
     * 			otherwise <code>false</code>.
	 */
	public final boolean isForeignStrategy() {
		Id idAnnotation = this.identifierField.getAnnotation(Id.class);
		return Strings.equals(idAnnotation.value(), GenerationType.FOREIGN);
	}
	
	public String identityStrategy() {
		Id id = identifierField.getAnnotation(Id.class);
		if(null != id) {
			return id.value();
		}
		return null;
	}

	
	/**
	 * Database column's name value
	 *
	 * @param fieldName a entity's property name
	 * @return column name
	 */ 
	public String column(String fieldName) {
		return fieldColumns.get(fieldName);
	}
	
	/**
	 * Find the matched field, class type of which is matched the the specified class type [fieldType] in a fields list.
	 *
	 * @param list fields list
	 * @param fieldType the specified class type
	 * @return
	 */
	public Field matchedField(List<Field> list, Class<?> fieldType) {
		for (Field field : list) {
			if(fieldType == field.getType()) {
				return field;
			}
		}
		return null;
	}
	
	/**
	 * Tests if the specified field is lazy
	 *
	 * @param field the specified field
	 * @return <code>true</code> if the specified field is lazy, 
	 * 			otherwise <code>false</code>.
	 */
	public static boolean isLazyField(Field field) {
		if(field.getAnnotation(Basic.class) != null) {
			if(field.getAnnotation(Basic.class).fetch() == FetchType.LAZY) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Useful for those Entities that only support one annotation of Id is defined.
	 * 
	 * @param clazz entity class type
	 * @return the identifier field
	 */
	public static Field getIdField(Class<?> clazz) {
		List<Field> keys = ClassMetadata.getClassMetadata(clazz).definedIdFields;
		if(keys.isEmpty())
			throw new JormException("No valid @Id defined in class " + clazz.getName());
		if(keys.size() > 1)
			throw new JormException("Multiple @Id defined in class " + clazz.getName());
		return keys.get(0);
	}
	
	/**
	 * Tests if the specified field has {@link FieldProcessor}
	 *
	 * @param field the specified field
	 * @return <code>true</code> if the specified field has {@link FieldProcessor}, 
	 * 			otherwise <code>false</code>.
	 */
	public static boolean hasProcessor(Field field) {
		if(field.getAnnotation(Basic.class) != null) {
			Class<?> processor = field.getAnnotation(Basic.class).processor();
			if(null != processor && processor != DummyFieldProcessor.class) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Gets entity's ClassMetadata by entity class type
	 *
	 * @param clazz entity class type
	 * @return the entity's ClassMetadata
	 */
	public static ClassMetadata getClassMetadata(Class<?> clazz) {
		ClassMetadata metadata = metadatas.get(clazz);
		if(null == metadata) {
			metadata = new ClassMetadata(clazz);
			metadatas.put(clazz, metadata);
		}
		return metadata;
	}
	
	/**
	 * Gets mapping table name for @ManyToMany
	 *
	 * @param obj   the target object
	 * @param mtmField a many-to-many relational field
	 * @return the many-to-many relation mapping table name
	 */
	public static String getMappingTablename(Object obj, Field mtmField) {
		String r = mtmField.getAnnotation(ManyToMany.class).table();
		if(Strings.isEmpty(r)) {
			String part1 = Strings.lowerCase(obj.getClass().getSimpleName());
			String part2 = Strings.lowerCase(mtmField.getAnnotation(ManyToMany.class).type().getSimpleName());
			String union = null;
			if(part1.compareTo(part2) < 0) {
				union = part1 + "_" + part2;
			} else {
				union = part2 + "_" + part1;
			}
			r = "t_mtm_r_" + union;
		}
		return r;
	}
	
	// ----------------- private method -----------------
	
	/**
	 * Process the class's annotation
	 *
	 * @param clazz domain class
	 * @return pk field names join string splited by ','
	 */
	private String processClassAnnotation(Class<?> clazz) {
		tableName = AnnotationModelHelper.getTableName(clazz);
		Entity entity = Annotations.findAnnotation(clazz, Entity.class);
		if(null != entity && entity.lazy()) {
			isLazyEntity = true;
		}
		PK pk = Annotations.findAnnotation(clazz, PK.class);
		String pkFieldNamesJoin = "";
		if(pk != null) {
			String[] pkFieldNames = pk.value();
			for (int i = 0; i < pkFieldNames.length; i++) {
				if(i > 0) {
					pkFieldNamesJoin = pkFieldNamesJoin + ",";
				}
				pkFieldNamesJoin = pkFieldNamesJoin + pkFieldNames[i];
				definedPkFields.add(Reflections.getField(clazz, pkFieldNames[i]));
			}
		}
		return pkFieldNamesJoin;
	}
	
	/**
	 * Process the class field's annotation
	 *
	 * @param clazz domain class
	 * @param pkjoin pk field names join string splited by ','
	 */
	private void processFieldAnnotation(Class<?> clazz, String pkjoin) {
		Class<?> theClass = clazz;
		while (null != theClass && !(theClass == Object.class)) {
			Field[] fs = theClass.getDeclaredFields();
			for (int i = 0; i < fs.length; i++) {
				if (isIgnoredField(fs[i])) continue;
				Id idAnn = fs[i].getAnnotation(Id.class);
				if(idAnn != null) {
					if(GenerationType.isAutoGenerated(idAnn.value())) {
						generatedFields.add(fs[i]);
					} else {
						insertFields.add(fs[i]);
					}
					definedIdFields.add(fs[i]);
					processIdentifierField(fs[i]);
				} else {
					if(fs[i].getAnnotation(Basic.class) != null) {
						if(fs[i].getAnnotation(Basic.class).fetch() == FetchType.LAZY) {
							hasLazyField = true;
							lazyFields.add(fs[i]);
						}
					}
					if(Annotations.hasAnnotation(fs[i], OneToOne.class)) {
						OneToOneFields.add(fs[i]);
					} else if(Annotations.hasAnnotation(fs[i], OneToMany.class)) {
						OneToManyFields.add(fs[i]);
					} else if(Annotations.hasAnnotation(fs[i], ManyToMany.class)) {
						ManyToManyFields.add(fs[i]);
					} else if(Annotations.hasAnnotation(fs[i], ManyToOne.class)) {
						ManyToOneFields.add(fs[i]);
					} else {
						insertFields.add(fs[i]);
					}
					
					if (pkjoin.indexOf(fs[i].getName()) < 0
							|| pkjoin.indexOf(fs[i].getName()) > pkjoin.length()) {
						updateFields.add(fs[i]);
					}
				}
				allFields.add(fs[i]);
				fieldColumns.put(fs[i].getName(), AnnotationModelHelper.getColumName(fs[i]));
			}
			theClass = theClass.getSuperclass();
		}
	}
	
	/**
	 * Tests if the specified field is needed to be managed by hand and {@link SqlParams}
	 *
	 * @param field the specified Field
	 * @return <code>true</code> if the specified field is needed to be managed by hand and {@link SqlParams}, 
	 * 			otherwise <code>false</code>.
	 */
	public static boolean isInsertField(Field field) {
		if(field.getAnnotation(NoColumn.class) != null) {
			return false;
		}
		Id id = field.getAnnotation(Id.class);
		if(id != null && GenerationType.isAutoGenerated(id.value())) {
			return false;
		}
		if(Annotations.hasAnnotation(field, OneToOne.class) || 
		   Annotations.hasAnnotation(field, OneToMany.class) || 
		   Annotations.hasAnnotation(field, ManyToMany.class) || 
		   Annotations.hasAnnotation(field, ManyToOne.class)) {
			return false;
		}
		return true;
	}
	
	private void processIdentifierField(Field field) {
		this.identifierField = field;
		this.getIdentifierMethod = Reflections.getMethod(this.clazz, field, MethodType.GET);
		this.setIdentifierMethod = Reflections.getMethod(this.clazz, field, MethodType.SET);
	}

	private static boolean isIgnoredField(Field f) {
		if(null == f) return true;
		if(f.getAnnotation(NoColumn.class) != null)
			return true;
		if (Modifier.isStatic(f.getModifiers()))
			return true;
		if (Modifier.isFinal(f.getModifiers()))
			return true;
		if(Modifier.isTransient(f.getModifiers()))
			return true;
		if (f.getName().startsWith("this$"))
			return true;
		return false;
	}

	public static void main(String[] args) {
		
	}
}
