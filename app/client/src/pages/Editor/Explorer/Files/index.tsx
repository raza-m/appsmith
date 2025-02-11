import React, { useCallback, useMemo } from "react";
import { useActiveAction } from "../hooks";
import { Entity } from "../Entity/index";
import {
  createMessage,
  ADD_QUERY_JS_TOOLTIP,
  ADD_QUERY_JS_BUTTON,
  EMPTY_QUERY_JS_BUTTON_TEXT,
  EMPTY_QUERY_JS_MAIN_TEXT,
} from "@appsmith/constants/messages";
import { useDispatch, useSelector } from "react-redux";
import {
  getCurrentApplicationId,
  getCurrentPageId,
} from "selectors/editorSelectors";
import { ExplorerActionEntity } from "../Actions/ActionEntity";
import ExplorerJSCollectionEntity from "../JSActions/JSActionEntity";
import { toggleShowGlobalSearchModal } from "actions/globalSearchActions";
import { Colors } from "constants/Colors";
import {
  comboHelpText,
  filterCategories,
  SEARCH_CATEGORY_ID,
} from "components/editorComponents/GlobalSearch/utils";
import { selectFilesForExplorer } from "selectors/entitiesSelector";
import { getExplorerStatus, saveExplorerStatus } from "../helpers";
import Icon from "components/ads/Icon";
import { noop } from "lodash";
import { AddEntity, EmptyComponent } from "../common";

function Files() {
  const applicationId = useSelector(getCurrentApplicationId);
  const pageId = useSelector(getCurrentPageId) as string;
  const files = useSelector(selectFilesForExplorer);
  const dispatch = useDispatch();
  const isFilesOpen = getExplorerStatus(applicationId, "queriesAndJs");

  const onCreate = useCallback(() => {
    dispatch(
      toggleShowGlobalSearchModal(
        filterCategories[SEARCH_CATEGORY_ID.ACTION_OPERATION],
      ),
    );
  }, [dispatch]);

  const activeActionId = useActiveAction();

  const onFilesToggle = useCallback(
    (isOpen: boolean) => {
      saveExplorerStatus(applicationId, "queriesAndJs", isOpen);
    },
    [applicationId],
  );

  const fileEntities = useMemo(
    () =>
      files.map(({ entity, type }: any) => {
        if (type === "group") {
          return (
            <div
              className={`text-sm text-[${Colors.CODE_GRAY}] pl-8 bg-trueGray-50 overflow-hidden overflow-ellipsis whitespace-nowrap`}
              key={entity.name}
            >
              {entity.name}
            </div>
          );
        } else if (type === "JS") {
          return (
            <ExplorerJSCollectionEntity
              id={entity.id}
              isActive={entity.id === activeActionId}
              key={entity.id}
              searchKeyword={""}
              step={2}
              type={type}
            />
          );
        } else {
          return (
            <ExplorerActionEntity
              id={entity.id}
              isActive={entity.id === activeActionId}
              key={entity.id}
              searchKeyword={""}
              step={2}
              type={type}
            />
          );
        }
      }),
    [files, activeActionId],
  );

  return (
    <Entity
      addButtonHelptext={
        <>
          {createMessage(ADD_QUERY_JS_TOOLTIP)} (
          {comboHelpText[SEARCH_CATEGORY_ID.ACTION_OPERATION]})
        </>
      }
      alwaysShowRightIcon
      className={`group files`}
      disabled={false}
      entityId={pageId + "_widgets"}
      icon={null}
      isDefaultExpanded={isFilesOpen === null ? true : isFilesOpen}
      isSticky
      key={pageId + "_widgets"}
      name="QUERIES/JS"
      onCreate={onCreate}
      onToggle={onFilesToggle}
      searchKeyword={""}
      step={0}
    >
      {fileEntities.length ? (
        fileEntities
      ) : (
        <EmptyComponent
          addBtnText={createMessage(EMPTY_QUERY_JS_BUTTON_TEXT)}
          addFunction={onCreate || noop}
          mainText={createMessage(EMPTY_QUERY_JS_MAIN_TEXT)}
        />
      )}
      {fileEntities.length > 0 && (
        <AddEntity
          action={onCreate}
          entityId={pageId + "_queries_js_add_new_datasource"}
          icon={<Icon name="plus" />}
          name={createMessage(ADD_QUERY_JS_BUTTON)}
          step={1}
        />
      )}
    </Entity>
  );
}

Files.displayName = "Files";

export default React.memo(Files);
