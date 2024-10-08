import { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";

import { clearValues, fetchEntities, connect, fetchEntityTypes, setActiveEnType } from "../../store/eav";

const EntityTypeTabs = () => {
  const dispatch = useDispatch();
  const connected = useSelector((state) => state.eav.connected);
  const loading = useSelector((state) => state.eav.loading);
  const tabs = useSelector((state) => state.eav.entityTypes);
  const [activeTab, setActiveTab] = useState(0);

  function loadEntities(id) {
    setActiveTab(id);
    dispatch(clearValues());
    dispatch(fetchEntities(id));
    dispatch(setActiveEnType(id));
  }

  useEffect(() => {
    if (!connected) dispatch(connect());
    else if (!loading) dispatch(fetchEntityTypes());
    // eslint-disable-next-line
  }, [connected])

  if (!connected) return <div className="tab-container">DB not connected</div>
  return (
    <div className="tab-container">
      {tabs.map(et => {
        let className = "tab";
        if (activeTab == et.id) className += " selected";
        return (
          <div className={className} key={et.id} onClick={() => loadEntities(et.id)}>
            {et.entityType}
          </div>
        )
      })}
    </div>
  )
}

export default EntityTypeTabs;