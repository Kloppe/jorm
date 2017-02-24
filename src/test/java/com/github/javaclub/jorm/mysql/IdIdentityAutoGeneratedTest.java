/*
 * @(#)IdIncrementTest.java	2011-8-13
 *
 * Copyright (c) 2011. All Rights Reserved.
 *
 */

package com.github.javaclub.jorm.mysql;

import java.util.ArrayList;
import java.util.List;

import com.github.javaclub.jorm.Jorm;
import com.github.javaclub.jorm.Session;
import com.github.javaclub.jorm.beans.id.IdIdentityAutoGenerated;
import com.github.javaclub.jorm.common.Strings;
import com.github.javaclub.jorm.jdbc.batch.JdbcBatcher;
import com.github.javaclub.jorm.jdbc.criterion.Order;
import com.github.javaclub.jorm.jdbc.sql.SqlParams;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * IdIncrementTest
 *
 * @author <a href="mailto:gerald.chen.hz@gmail.com">Gerald Chen</a>
 * @version $Id: IdIdentityAutoGeneratedTest.java 474 2011-09-25 14:45:05Z gerald.chen.hz@gmail.com $
 */
public class IdIdentityAutoGeneratedTest {

	static Session session;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		session = Jorm.getSession();
	}
	
	@AfterClass
	public static void destroyAfterClass() {
		Jorm.free();
	}
	
	@Test
	public void save() {
		session.clean(IdIdentityAutoGenerated.class);
		IdIdentityAutoGenerated u;
		for(int i = 0; i < 10000; i++) {
			u = new IdIdentityAutoGenerated(Strings.fixed(6));
			session.save(u);
			System.out.println(u);
		}
		
	}
	
	@Test
	public void batchSave() {
		session.clean(IdIdentityAutoGenerated.class);
		IdIdentityAutoGenerated u = null;
		session.beginTransaction();
		JdbcBatcher batcher = session.createBatcher();
		for(int i = 0; i < 1000; i++) {
			u = new IdIdentityAutoGenerated(Strings.fixed(6));
			batcher.save(u);
			System.out.println(u);
		}
		batcher.execute();
		session.commit();
		session.endTransaction();
		System.out.println(u);
	}
	
	@Test
	public void update() {
		session.clean(IdIdentityAutoGenerated.class);
		IdIdentityAutoGenerated user = new IdIdentityAutoGenerated(Strings.fixed(6));
		System.out.println(session.save(user));
		
		IdIdentityAutoGenerated u = session.read(IdIdentityAutoGenerated.class, 1);
		u.setName("driver");
		
		session.update(u);
		IdIdentityAutoGenerated usr = session.read(IdIdentityAutoGenerated.class, 1);
		System.out.println(usr);
	}
	
	@Test
	public void delete_sql_1() {
		session.clean(IdIdentityAutoGenerated.class);
		IdIdentityAutoGenerated u;
		for(int i = 0; i < 1000; i++) {
			u = new IdIdentityAutoGenerated(Strings.fixed(6));
			session.save(u);
		}
		
		int count = session.delete("DELETE FROM t_id_increment_mysql_auto");
		System.out.println(count);
	}
	
	@Test
	public void delete_sql_2() {
		session.clean(IdIdentityAutoGenerated.class);
		IdIdentityAutoGenerated u;
		for(int i = 0; i < 1000; i++) {
			u = new IdIdentityAutoGenerated(Strings.fixed(6));
			session.save(u);
		}
		int count = session.delete(IdIdentityAutoGenerated.class, "id > 100");
		System.out.println(count);
	}
	
	@Test
	public void load_first() {
		session.clean(IdIdentityAutoGenerated.class);
		IdIdentityAutoGenerated u;
		for(int i = 0; i < 1000; i++) {
			u = new IdIdentityAutoGenerated(Strings.fixed(6));
			session.save(u);
		}
		IdIdentityAutoGenerated user = session.loadFirst(IdIdentityAutoGenerated.class, "(SELECT * FROM t_id_increment_mysql_auto WHERE id > ?)", 100);
		System.out.println(user);
	}
	
	@Test
	public void list_1() {
		session.clean(IdIdentityAutoGenerated.class);
		IdIdentityAutoGenerated u;
		for(int i = 0; i < 1000; i++) {
			u = new IdIdentityAutoGenerated(Strings.fixed(6));
			session.save(u);
		}
		List<IdIdentityAutoGenerated> users = session.list(new SqlParams<IdIdentityAutoGenerated>("SELECT * FROM t_id_increment_mysql_auto WHERE id > ?", new Object[] {100}).setObjectClass(IdIdentityAutoGenerated.class));
		System.out.println(users.size());
	}
	
	@Test
	public void page_0() {
		
		SqlParams<IdIdentityAutoGenerated> params = new SqlParams<IdIdentityAutoGenerated>("SELECT * FROM t_user where id > 6");
		params.setObjectClass(IdIdentityAutoGenerated.class);
		params.setFirstResult(3);
		params.setMaxResults(6);
		params.addOrder(Order.desc("id"));
		List<IdIdentityAutoGenerated> page = session.list(params);
		for (IdIdentityAutoGenerated user : page) {
			System.out.println(user);
		}
	}
	
	
	
	@Test
	public void batch_insert_1() {
		session.clean(IdIdentityAutoGenerated.class);
		String sql = "INSERT INTO t_id_increment_mysql_auto(name) VALUES(?)";
		List<Object[]> datalist = new ArrayList<Object[]>();
		for (int i = 0; i < 1000; i++) {
			datalist.add(new Object[] {Strings.fixed(6)});
		}
		session.batchInsert(sql, datalist);
	}
	
	@Test
	public void tx_1() {
		session.clean(IdIdentityAutoGenerated.class);
		IdIdentityAutoGenerated u;
		session.beginTransaction();
		try {
			for(int i = 0; i < 1000; i++) {
				u = new IdIdentityAutoGenerated(Strings.fixed(6));
				session.save(u);
				if(i == 886) {
					//Integer.parseInt("kkk");
				}
			}
		} catch (Exception e) {
			session.rollback();
		} finally {
			session.endTransaction();
		}
	}
}