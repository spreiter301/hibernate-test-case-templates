package org.hibernate.bugs;

import org.hibernate.Hibernate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.RollbackException;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using the Java Persistence API.
 */
public class JPAUnitTestCase {

	private EntityManagerFactory entityManagerFactory;

	@Before
	public void init() {
		entityManagerFactory = Persistence.createEntityManagerFactory("templatePU");
	}

	@After
	public void destroy() {
		entityManagerFactory.close();
	}

	@Test
	public void persistenceContext() throws Exception {
		final Long parentId = cascadePersist();
		updateWithoutSave(parentId);
		cascadeDetachMerge(parentId);
		repeatableReads(parentId);
		orphanRemoval(parentId);
		cascadeRemove(parentId);
	}

	private Long cascadePersist() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		Parent parent = new Parent();
		parent.setName("Hans");
		Child child = new Child(parent);
		parent.getChildren().add(child);
		// Cascade Persist
		entityManager.persist(parent);

		entityManager.getTransaction().commit();
		entityManager.close();

		entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		parent = entityManager.find(Parent.class, parent.getId());
		assertEquals("Hans", parent.getName());
		assertEquals(1, parent.getChildren().size());

		entityManager.getTransaction().commit();
		entityManager.close();

		return parent.getId();
	}

	private void updateWithoutSave(Long parentId) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		Parent parent = entityManager.find(Parent.class, parentId);

		assertTrue(entityManager.contains(parent)); // Im PersistenceContext

		parent.setName("Peter"); // Hibernate Changetracking
		final Child child = new Child(parent);
		parent.getChildren().add(child); // Cascade.PERSIST

		// entityManager.flush();

		entityManager.getTransaction().commit();
		entityManager.close();

		entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		parent = entityManager.find(Parent.class, parent.getId());
		assertEquals("Peter", parent.getName());
		assertEquals(2, parent.getChildren().size());

		entityManager.getTransaction().commit();
		entityManager.close();
	}

	private void cascadeDetachMerge(Long parentId) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		Parent detached = entityManager.find(Parent.class, parentId);
		assertTrue(entityManager.contains(detached));
		detached.getChildren().forEach(child -> assertTrue(entityManager.contains(child)));
		entityManager.detach(detached);
		assertFalse(entityManager.contains(detached));
		detached.getChildren().forEach(child -> assertFalse(entityManager.contains(child))); // Cascade.DETACH

		detached.setName("Ueli");
		detached.getChildren().forEach(child -> child.setName("new name"));

		Parent merged = entityManager.merge(detached); // Sucht nach dem Entity und kopiert den State von detached auf merged
		// Parent merged = entityManager.find(Parent.class, parentId);
		// if (merged == null) merged = new Parent(); // und in den PersistenceContext speichern
		// copyProperties(detached, merged);
		assertFalse(entityManager.contains(detached));
		assertTrue(entityManager.contains(merged));
		merged.getChildren().forEach(child -> assertTrue(entityManager.contains(child)));

		assertEquals("Ueli", merged.getName());
		merged.getChildren().forEach(child -> assertEquals("new name", child.getName())); // Casecade.MERGE

		entityManager.getTransaction().commit();
		entityManager.close();
	}

	private void repeatableReads(Long parentId) {
		// https://de.wikipedia.org/wiki/Isolation_(Datenbank)#Repeatable_Read
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		Parent parent = entityManager.find(Parent.class, parentId);
		Parent parent2 = entityManager.find(Parent.class, parentId);

		assertNotNull(parent);
		assertEquals(parent, parent2);
		assertTrue(parent == parent2);

		final List<Child> children = entityManager.createQuery("SELECT c FROM Child c", Child.class).getResultList();
		assertTrue(parent.getChildren().containsAll(children));

		entityManager.getTransaction().commit();
		entityManager.close();

		// parent und parent2 sind nun detached

		entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		Parent parent3 = entityManager.find(Parent.class, parentId);

		assertNotNull(parent3);
		assertEquals(parent, parent3);
		assertFalse(parent == parent3);

		entityManager.getTransaction().commit();
		entityManager.close();
	}

	private void orphanRemoval(Long parentId) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		Parent parent = entityManager.find(Parent.class, parentId);
		parent.getChildren().remove(1);
		assertEquals(1, parent.getChildren().size());

		entityManager.getTransaction().commit();
		entityManager.close();

		entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		parent = entityManager.find(Parent.class, parentId);
		assertEquals(1, parent.getChildren().size());

		entityManager.getTransaction().commit();
		entityManager.close();
	}

	private void cascadeRemove(Long parentId) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		Parent parent = entityManager.find(Parent.class, parentId);
		entityManager.remove(parent); // Cascade.REMOVE

		assertFalse(entityManager.contains(parent));

		entityManager.getTransaction().commit();
		entityManager.close();

		entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		final List<Child> children = entityManager.createQuery("SELECT c FROM Child c", Child.class).getResultList();

		assertEquals(0, children.size());

		entityManager.getTransaction().commit();
		entityManager.close();
	}

	@Test(expected = RollbackException.class)
	public void mergePersistenEntity() {
		final Long parentId = cascadePersist();

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		final Parent parent = entityManager.find(Parent.class, parentId);
		assertFalse(Hibernate.isInitialized(parent.getChildren()));
		parent.getChildren().add(new Child(parent));
		assertFalse(Hibernate.isInitialized(parent.getChildren()));
		parent.getChildren().forEach(System.out::println); // LAZY children werden hier initialisiert
		assertTrue(Hibernate.isInitialized(parent.getChildren()));
		entityManager.merge(parent); // Fehler passiert hier. parent ist bereits Persisten und wird beim Commit geupdatet

		entityManager.getTransaction().commit();
		entityManager.close();
	}

	@Test
	public void clearPersistenceContext() {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		IntStream.range(0, 1_000_000_000).forEach(i -> {
			entityManager.persist(new Parent());
			if (i % 1000 == 0) {
				entityManager.flush();
				entityManager.clear();
			}
		});

		entityManager.getTransaction().commit();
		entityManager.close();
	}

	@Test
	public void muteRenderers() {
		final Long parentId = cascadePersist();

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		final Parent parent = entityManager.find(Parent.class, parentId);
		final List<Child> children = parent.getChildren();
		assertFalse(Hibernate.isInitialized(children));
		children.size();
		assertTrue(Hibernate.isInitialized(children));

		entityManager.getTransaction().commit();
		entityManager.close();
	}
}
