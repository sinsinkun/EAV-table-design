package org.access;

import org.database.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.util.AnsiColors;
import org.util.Fn;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@RestController
@CrossOrigin
public class Controller {

    EavInterface eav;

    @RequestMapping(method=RequestMethod.POST, path="/connect")
    public ResponseEntity<?> login(@RequestBody DbAccess auth) {
        DbSetup setup = new DbSetup();
        setup.server = auth.getHost();
        setup.dbName = auth.getDbName();
        setup.user = auth.getUser();
        setup.password = auth.getPassword();
        if (!setup.isValid()) {
            return ResponseEntity.status(400).body("Missing required info");
        }
        // use existing connection if available
        if (eav != null && eav.server == setup.server && eav.dbName == setup.dbName) {
            Fn.printColor(AnsiColors.GREEN, "Already connected to DB");
            return ResponseEntity.status(200).body("OK");
        }
        // connect to database
        try {
            eav = new EavInterface(setup);
            Fn.printColor(AnsiColors.GREEN, "Connected to DB");
            return ResponseEntity.status(200).body("OK");
        } catch(Exception e) {
            Fn.printColor(AnsiColors.RED, "Err: Could not connect to DB -- " + e.getMessage());
            return ResponseEntity.status(500).body("Could not connect to DB");
        }
    }

    @RequestMapping(method=RequestMethod.GET, path="/view/all")
    public List<EavView> getAll() {
        if (eav == null) throw new EavException();
        return eav.getEverything();
    }

    @RequestMapping(method=RequestMethod.GET, path="/view/entities")
    public List<EavView> getViewEntities() {
        if (eav == null) throw new EavException();
        List<EavEntityType> entityTypes = eav.getEntityTypes();
        List<EavEntity> entities = eav.getEntities();

        // build views
        List<EavView> views = new ArrayList<>();
        for (EavEntity entity : entities) {
            List<EavEntityType> etList = entityTypes.stream()
                    .filter(x -> x.getId() == entity.getEntityTypeId())
                    .collect(Collectors.toList());
            EavView v = new EavView();
            v.setEntityTypeId(Integer.valueOf(entity.getEntityTypeId()));
            v.setEntityId(Integer.valueOf(entity.getId()));
            v.setEntity(entity.getEntity());
            v.setCreatedAt(entity.getCreatedAt());
            if (!etList.isEmpty()) v.setEntityType(etList.get(0).getEntityType());
            views.add(v);
        }

        return views;
    }

    @RequestMapping(method=RequestMethod.GET, path="/view/entity/{id}")
    public List<EavView> getViewEntity(@PathVariable("id") Integer entityId) {
        if (eav == null) throw new EavException();
        List<EavView> views = eav.getEntityViewById(entityId);
        return views;
    }

    @RequestMapping(method=RequestMethod.GET, path="/entity-types")
    public List<EavEntityType> getAllEntityTypes() {
        if (eav == null) throw new EavException();
        return eav.getEntityTypes();
    }

    @RequestMapping(method=RequestMethod.GET, path="/entities")
    public List<EavEntity> getAllEntities() {
        if (eav == null) throw new EavException();
        return eav.getEntities();
    }

    @RequestMapping(method=RequestMethod.POST, path="/entity")
    public EavEntity createEntity(@RequestBody EavView builder) {
        if (eav == null) throw new EavException();
        return eav.createEntity(builder.getEntityType(), builder.getEntity());
    }

    @RequestMapping(method=RequestMethod.PUT, path="/entity")
    public EavEntity updateEntity(@RequestBody EavEntity entity) {
        if (eav == null) throw new EavException();
        return eav.updateEntity(entity);
    }

    @RequestMapping(method=RequestMethod.GET, path="/entities/{type_id}")
    public List<EavEntity> getEntitiesForType(@PathVariable("type_id") Integer typeId) {
        if (eav == null) throw new EavException();
        EavEntityType et = eav.getEntityTypeById(typeId);
        return eav.getEntities(et);
    }

    @RequestMapping(method=RequestMethod.GET, path="/attributes/{entity_id}")
    public List<EavAttribute> getAttrsForEntity(@PathVariable("entity_id") Integer entityId) {
        if (eav == null) throw new EavException();
        EavEntity e = eav.getEntityById(entityId);
        return eav.getAttributes(e);
    }

    @RequestMapping(method=RequestMethod.POST, path="/attribute")
    public EavAttribute createAttribute(@RequestBody EavView builder) {
        if (eav == null) throw new EavException();
        Integer entityTypeId;
        if (builder.getEntityTypeId() != null) {
            entityTypeId = builder.getEntityTypeId();
        } else if (builder.getEntityId() != null) {
            EavEntity entity = eav.getEntityById(builder.getEntityId());
            entityTypeId = entity.getEntityTypeId();
        } else {
            throw new EavException("Missing entityTypeId");
        }
        return eav.createAttribute(entityTypeId, builder.getAttr(), builder.getValueType(), builder.getAllowMultiple());
    }

    @RequestMapping(method=RequestMethod.PUT, path="/attribute")
    public EavAttribute updateAttribute(@RequestBody EavAttribute attr) {
        if (eav == null) throw new EavException();
        return eav.updateAttribute(attr);
    }

    @RequestMapping(method=RequestMethod.POST, path="/value")
    public EavValue createValue(@RequestBody EavValue v) {
        if (eav == null) throw new EavException();
        return eav.unsafeCreateValue(v);
    }

    @RequestMapping(method=RequestMethod.PUT, path="/value")
    public EavValue updateValue(@RequestBody EavValue v) {
        if (eav == null) throw new EavException();
        return eav.updateValue(v);
    }
}
