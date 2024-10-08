package org.database;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.sql2o.Connection;
import org.sql2o.Sql2o;

@SuppressWarnings("unused")
public class EavInterface {
    private final Connection conn;

    public final String server;
    public final String dbName;
    public final String entityTypeTable;
    public final String entityTable;
    public final String attributeTable;
    public final String valueTable;

    // constructor
    public EavInterface(DbSetup setup) {
        if (setup.server.isEmpty() || setup.dbName.isEmpty()) {
            throw new IllegalArgumentException("Required arguments not provided");
        }
        entityTypeTable = setup.entityTypeTable;
        entityTable = setup.entityTable;
        attributeTable = setup.attributeTable;
        valueTable = setup.valueTable;
        server = setup.server;
        dbName = setup.dbName;

        Sql2o db = new Sql2o(
                "jdbc:mysql://" + setup.server + "/" + setup.dbName + "?allowPublicKeyRetrieval=true",
                setup.user, setup.password
        );
        try (Connection c = db.open()) {
            c.setRollbackOnException(false);
            conn = c;
        }
    }

    public <T> List<T> get(Class<T> schema, String target) {
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Query target not provided");
        }
        String query = "SELECT * FROM " + target;
        return conn.createQuery(query).executeAndFetch(schema);
    }

    private Integer getLastId() {
        return conn.createQuery("SELECT last_insert_id();").executeAndFetchFirst(Integer.class);
    }

    // region entityType
    public List<EavEntityType> getEntityTypes() {
        String query = "SELECT * FROM " + entityTypeTable;
        return conn.createQuery(query).executeAndFetch(EavEntityType.class);
    }

    public List<EavEntityType> getEntityTypesByIds(Collection<Integer> ids) {
        String query = "SELECT * FROM " + entityTypeTable + " WHERE id IN (:ids)";
        StringBuilder idsStr = new StringBuilder();
        for (Integer id : ids) {
            idsStr.append(id.toString()).append(",");
        }
        idsStr.deleteCharAt(idsStr.length() - 1);
        return conn.createQuery(query)
                .addParameter("ids", idsStr)
                .executeAndFetch(EavEntityType.class);
    }

    public EavEntityType getEntityTypeById(int id) {
        String query = "SELECT * FROM " + entityTypeTable + " WHERE id = :id";
        return conn.createQuery(query)
                .addParameter("id", id)
                .executeAndFetchFirst(EavEntityType.class);
    }

    public EavEntityType createEntityType(String name) {
        String query = "INSERT INTO " + entityTypeTable + " (entity_type) "
                + "VALUES (:entity_type)";
        conn.createQuery(query)
                .addParameter("entity_type", name)
                .executeUpdate();

        return getEntityTypeById(getLastId());
    }

    public boolean deleteEntityType(EavEntityType entityType) {
        String query1 = "DELETE FROM " + entityTypeTable + " WHERE id = :id";
        int results = conn.createQuery(query1)
                .addParameter("id", entityType.getId())
                .executeUpdate()
                .getResult();

        return results > 0;
    }
    // endregion entityType

    // region entity
    public List<EavEntity> getEntities() {
        String query = "SELECT * FROM " + entityTable;
        return conn.createQuery(query).executeAndFetch(EavEntity.class);
    }

    public List<EavEntity> getEntities(EavEntityType entityType) {
        String query = "SELECT * FROM " + entityTable + " WHERE entity_type_id = " + entityType.getId();
        return conn.createQuery(query).executeAndFetch(EavEntity.class);
    }

    public EavEntity getEntityById(int entityId) {
        String query = "SELECT * FROM " + entityTable + " WHERE id = :id";
        return conn.createQuery(query)
                .addParameter("id", entityId)
                .executeAndFetchFirst(EavEntity.class);
    }

    public EavEntity createEntity(String entity_type, String entity) {
        if (entity.isEmpty() || entity_type.isEmpty()) {
            throw new IllegalArgumentException("Err: parameters cannot be empty");
        }
        String query = "CALL create_eav_entity(:entity_type, :entity);";
        conn.createQuery(query)
                .addParameter("entity_type", entity_type)
                .addParameter("entity", entity)
                .executeUpdate();

        // fetch new resources
        String query_a = "SELECT * FROM " + entityTypeTable + " WHERE entity_type = :entity_type";
        List<EavEntityType> list_a = conn.createQuery(query_a)
                .addParameter("entity_type", entity_type)
                .executeAndFetch(EavEntityType.class);
        if (list_a.isEmpty()) {
            throw new RuntimeException("Err: Failed to create entity_type");
        }

        String query_b = "SELECT * FROM " + entityTable + " WHERE entity = :entity AND entity_type_id = :entity_type_id";
        List<EavEntity> list_b = conn.createQuery(query_b)
                .addParameter("entity", entity)
                .addParameter("entity_type_id", list_a.get(0).getId())
                .executeAndFetch(EavEntity.class);
        if (list_b.isEmpty()) {
            throw new RuntimeException("Err: Failed to create entity");
        }

        return list_b.get(0);
    }

    public EavEntity updateEntity(EavEntity updated) {
        String query = "UPDATE " + entityTable + " SET entity = :entity WHERE id = :id";
        conn.createQuery(query)
                .addParameter("entity", updated.getEntity())
                .addParameter("id", updated.getId())
                .executeUpdate();
        return getEntityById(getLastId());
    }

    public boolean deleteEntity(EavEntity entity) {
        // delete all values for entity
        String query1 = "DELETE FROM " + valueTable + " WHERE entity_id = :entity_id";
        int results = conn.createQuery(query1)
                .addParameter("entity_id", entity.getId())
                .executeUpdate()
                .getResult();
        System.out.println("Deleted values: " + results);
        // delete entity
        String query2 = "DELETE FROM " + entityTable + " WHERE id = :entity_id";
        int results2 = conn.createQuery(query2)
                .addParameter("entity_id", entity.getId())
                .executeUpdate()
                .getResult();
        System.out.println("Deleted entity: " + results2);

        return results2 > 0;
    }

    public boolean deleteEntities(Collection<EavEntity> entities) {
        if (entities.isEmpty()) {
            return true;
        }

        StringBuilder idList = new StringBuilder();
        for (EavEntity e : entities) {
            idList.append(e.getId()).append(",");
        }
        idList.deleteCharAt(idList.length() - 1);

        // delete all values for entities
        String query1 = "DELETE FROM " + valueTable + " WHERE entity_id IN (:entity_ids)";
        int results = conn.createQuery(query1)
                .addParameter("entity_ids", idList)
                .executeUpdate()
                .getResult();
        System.out.println("Deleted values: " + results);
        // delete entity
        String query2 = "DELETE FROM " + entityTable + " WHERE id IN (:entity_ids)";
        int results2 = conn.createQuery(query2)
                .addParameter("entity_ids", idList)
                .executeUpdate()
                .getResult();
        System.out.println("Deleted entities: " + results2);

        return results2 > 0;
    }
    // endregion entity

    // region attribute
    public List<EavAttribute> getAttributes() {
        String query = "SELECT * FROM " + attributeTable;
        return conn.createQuery(query).executeAndFetch(EavAttribute.class);
    }

    public List<EavAttribute> getAttributes(EavEntityType entityType) {
        String query = "SELECT * FROM " + attributeTable + " WHERE entity_type_id = :id";
        return conn.createQuery(query)
                .addParameter("id", entityType.getId())
                .executeAndFetch(EavAttribute.class);
    }

    public List<EavAttribute> getAttributes(EavEntity entity) {
        String query = "SELECT * FROM " + attributeTable + " WHERE entity_type_id = :id";
        return conn.createQuery(query)
                .addParameter("id", entity.getEntityTypeId())
                .executeAndFetch(EavAttribute.class);
    }

    public EavAttribute getAttributeById(int attrId) {
        String query = "SELECT * FROM " + attributeTable + " WHERE id = :id";
        return conn.createQuery(query)
                .addParameter("id", attrId)
                .executeAndFetchFirst(EavAttribute.class);
    }

    public EavAttribute createAttribute(Integer entityTypeId, String attributeName, ValueType attributeType, boolean allowMultiple) {
        if (entityTypeId == null || attributeName.isEmpty() || attributeType == null) {
            throw new IllegalArgumentException("Err: invalid parameters");
        }
        String query = "CALL create_eav_attr(:attr, :attr_type, :entity_type_id, :allow_multiple);";
        conn.createQuery(query)
                .addParameter("attr", attributeName)
                .addParameter("attr_type", attributeType.getValue())
                .addParameter("entity_type_id", entityTypeId)
                .addParameter("allow_multiple", allowMultiple)
                .executeUpdate();

        return getAttributeById(getLastId());
    }

    public EavAttribute updateAttribute(EavAttribute updated) {
        String query = "UPDATE " + attributeTable + " SET value_type = :vt, allow_multiple = :am WHERE id = :id";
        conn.createQuery(query)
                .addParameter("vt", updated.getValueType())
                .addParameter("am", updated.isAllowMultiple())
                .addParameter("id", updated.getId())
                .executeUpdate();
        return getAttributeById(getLastId());
    }

    public boolean deleteAttribute(EavAttribute attribute) {
        // delete all values for attribute
        String query1 = "DELETE FROM " + valueTable + " WHERE attr_id = :attr_id";
        int results = conn.createQuery(query1)
                .addParameter("attr_id", attribute.getId())
                .executeUpdate()
                .getResult();
        System.out.println("Deleted values: " + results);
        // delete attribute
        String query2 = "DELETE FROM " + attributeTable + " WHERE id = :attr_id";
        int results2 = conn.createQuery(query2)
                .addParameter("attr_id", attribute.getId())
                .executeUpdate()
                .getResult();
        System.out.println("Deleted attribute: " + results2);

        return results2 > 0;
    }

    public boolean deleteAttributes(Collection<EavAttribute> attributes) {
        if (attributes.isEmpty()) {
            return true;
        }

        StringBuilder idList = new StringBuilder();
        for (EavAttribute a : attributes) {
            idList.append(a.getId()).append(",");
        }
        idList.deleteCharAt(idList.length() - 1);

        // delete all values for entities
        String query1 = "DELETE FROM " + valueTable + " WHERE attr_id IN (:attr_ids)";
        int results = conn.createQuery(query1)
                .addParameter("attr_ids", idList)
                .executeUpdate()
                .getResult();
        System.out.println("Deleted values: " + results);
        // delete entity
        String query2 = "DELETE FROM " + attributeTable + " WHERE id IN (:attr_ids)";
        int results2 = conn.createQuery(query2)
                .addParameter("attr_ids", idList)
                .executeUpdate()
                .getResult();
        System.out.println("Deleted attributes: " + results2);

        return results2 > 0;
    }
    // endregion attribute

    // region value
    public List<EavValue> getValues(EavEntity entity) {
        String query = "SELECT * FROM " + valueTable + " WHERE entity_id = " + entity.getId();
        return conn.createQuery(query).executeAndFetch(EavValue.class);
    }

    public EavValue getValueById(int valueId) {
        String query = "SELECT * FROM " + valueTable + " WHERE id = :id";
        return conn.createQuery(query)
                .addParameter("id", valueId)
                .executeAndFetchFirst(EavValue.class);
    }

    public EavValue createValue(EavEntity entity, EavAttribute attribute, Object value) {
        if (entity.getId() == 0 || attribute.getId() == 0 || entity.getEntityTypeId() != attribute.getEntityTypeId()) {
            throw new IllegalArgumentException("Err: invalid parameters");
        }

        String v1 = null;
        Integer v2 = null;
        Float v3 = null;
        Instant v4 = null;
        Boolean v5 = null;

        // fill value
        if (value instanceof String && attribute.getValueType() == ValueType.STR) v1 = (String) value;
        else if (value instanceof Integer && attribute.getValueType() == ValueType.INT) v2 = (Integer) value;
        else if (value instanceof Float && attribute.getValueType() == ValueType.FLOAT) v3 = (Float) value;
        else if (value instanceof Instant && attribute.getValueType() == ValueType.TIME) v4 = (Instant) value;
        else if (value instanceof Boolean && attribute.getValueType() == ValueType.BOOL) v5 = (Boolean) value;
        else {
            throw new IllegalArgumentException("Err: invalid value provided");
        }

        String query = "CALL create_eav_value(:entity_id, :attr_id, :v1, :v2, :v3, :v4, :v5);";
        conn.createQuery(query)
                .addParameter("entity_id", entity.getId())
                .addParameter("attr_id", attribute.getId())
                .addParameter("v1", v1)
                .addParameter("v2", v2)
                .addParameter("v3", v3)
                .addParameter("v4", v4)
                .addParameter("v5", v5)
                .executeUpdate()
                .getKey();

        return getValueById(getLastId());
    }

    // bypass java validation
    public EavValue unsafeCreateValue(EavValue value) {
        String query = "CALL create_eav_value(:entity_id, :attr_id, :v1, :v2, :v3, :v4, :v5);";
        conn.createQuery(query)
                .addParameter("entity_id", value.getEntityId())
                .addParameter("attr_id", value.getAttrId())
                .addParameter("v1", value.getValueStr())
                .addParameter("v2", value.getValueInt())
                .addParameter("v3", value.getValueFloat())
                .addParameter("v4", value.getValueTime())
                .addParameter("v5", value.getValueBool())
                .executeUpdate();

        return getValueById(getLastId());
    }

    public EavValue updateValue(EavValue updated) {
        String query = "UPDATE " + valueTable + " SET " +
                " value_str = :v1, value_int = :v2, value_float = :v3, value_time = :v4, value_bool = :v5 " +
                "WHERE id = :id";
        conn.createQuery(query)
                .addParameter("v1", updated.getValueStr())
                .addParameter("v2", updated.getValueInt())
                .addParameter("v3", updated.getValueFloat())
                .addParameter("v4", updated.getValueTime())
                .addParameter("v5", updated.getValueBool())
                .addParameter("id", updated.getId())
                .executeUpdate();
        return getValueById(getLastId());
    }

    public boolean deleteValue(EavValue value) {
        String query1 = "DELETE FROM " + valueTable + " WHERE entity_id = :value_id";
        int result = conn.createQuery(query1)
                .addParameter("entity_id", value.getId())
                .executeUpdate()
                .getResult();

        return result > 0;
    }

    public boolean deleteValues(Collection<EavValue> values) {
        if (values.isEmpty()) {
            return true;
        }

        StringBuilder idList = new StringBuilder();
        for (EavValue v : values) {
            idList.append(v.getId()).append(",");
        }
        idList.deleteCharAt(idList.length() - 1);

        String query1 = "DELETE FROM " + valueTable + " WHERE id IN (:ids)";
        int results = conn.createQuery(query1)
                .addParameter("ids", idList)
                .executeUpdate()
                .getResult();

        return results > 0;
    }
    // endregion value

    // region view
    public List<EavView> getEverything() {
        String query = "SELECT * FROM all_possible_eav_data";
        return conn.createQuery(query).executeAndFetch(EavView.class);
    }

    public List<EavView> getEveryValue() {
        String query = "SELECT * FROM all_existing_eav_data";
        return conn.createQuery(query).executeAndFetch(EavView.class);
    }

    public List<EavView> getEntityView(EavEntity entity) {
        String query = "SELECT * FROM all_possible_eav_data WHERE entity_id = :entity_id";
        return conn.createQuery(query)
                .addParameter("entity_id", entity.getId())
                .executeAndFetch(EavView.class);
    }

    public List<EavView> getEntityViewById(Integer entityId) {
        String query = "SELECT * FROM all_possible_eav_data WHERE entity_id = :entity_id";
        return conn.createQuery(query)
                .addParameter("entity_id", entityId)
                .executeAndFetch(EavView.class);
    }
    // endregion view
}
