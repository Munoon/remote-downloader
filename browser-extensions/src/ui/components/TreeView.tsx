"use client";
// @subframe/sync-disable
/*
 * Documentation:
 * Tree View — https://app.subframe.com/dd13c78ea6fd/library?component=Tree+View_4ed46422-ecc3-41e8-8787-e55ee10cdc75
 */

import React, { MouseEventHandler } from "react";
import * as SubframeUtils from "../utils";
import * as SubframeCore from "@subframe/core";
import { FeatherFolder } from "@subframe/core";
import { Accordion } from "./Accordion";
import { FeatherFile } from "@subframe/core";
import { Loader } from "@/ui/components/Loader";
import ErrorIcon from "./ErrorIcon";

interface FolderProps extends React.ComponentProps<typeof Accordion> {
  children?: React.ReactNode;
  selected: boolean;
  open: boolean;
  label?: React.ReactNode;
  icon?: React.ReactNode;
  loading: boolean;
  className?: string;
  onFolderClick: MouseEventHandler<HTMLDivElement>
  rightIcon: React.ReactNode
  errorMessage?: string
  validationError?: boolean
}

const Folder = React.forwardRef<HTMLElement, FolderProps>(function Folder(
  {
    children,
    label,
    icon = <FeatherFolder />,
    selected = false,
    open = false,
    onFolderClick,
    rightIcon,
    errorMessage,
    className,
    validationError = false,
    loading,
    ...otherProps
  }: FolderProps,
  ref
) {
  return (
    <Accordion
      className={SubframeUtils.twClassNames(
        "group/c841484c cursor-pointer",
        className
      )}
      trigger={
        <div
          className={SubframeUtils.twClassNames(
            "flex w-full items-center gap-2 rounded-md px-3 py-2",
            { "bg-brand-100": selected, "border border-error-600": validationError }
          )}
          onClick={onFolderClick}
        >
          {icon ? (
            <SubframeCore.IconWrapper className={SubframeUtils.twClassNames(
                "text-body font-body text-default-font",
                { "text-brand-700": selected, "text-error-600": validationError }
            )}>
              {icon}
            </SubframeCore.IconWrapper>
          ) : null}
          {label}
          <div className="basis-0 grow flex-[0_0_auto] inline-flex items-center">
            {loading && <Loader size="small" />}
            {errorMessage && <ErrorIcon text={errorMessage} />}
          </div>
          {rightIcon}
          <Accordion.Chevron className={validationError ? 'text-error-600' : ''} />
        </div>
      }
      open={open}
      ref={ref as any}
      {...otherProps}
    >
      {children ? (
        <div className="flex w-full flex-col items-start gap-1 pl-6 pt-1">
          {children}
        </div>
      ) : null}
    </Accordion>
  );
});

interface ItemProps extends React.HTMLAttributes<HTMLDivElement> {
  selected?: boolean;
  label?: React.ReactNode;
  icon?: React.ReactNode;
  className?: string;
}

const Item = React.forwardRef<HTMLElement, ItemProps>(function Item(
  {
    selected = false,
    label,
    icon = <FeatherFile />,
    className,
    ...otherProps
  }: ItemProps,
  ref
) {
  return (
    <div
      className={SubframeUtils.twClassNames(
        "group/42786044 flex w-full cursor-pointer items-center gap-2 rounded-md px-3 py-2 hover:bg-neutral-50",
        { "bg-brand-100 hover:bg-brand-100": selected },
        className
      )}
      ref={ref as any}
      {...otherProps}
    >
      {icon ? (
        <SubframeCore.IconWrapper
          className={SubframeUtils.twClassNames(
            "text-body font-body text-default-font",
            { "text-brand-700": selected }
          )}
        >
          {icon}
        </SubframeCore.IconWrapper>
      ) : null}
      {label ? (
        <span
          className={SubframeUtils.twClassNames(
            "line-clamp-1 grow shrink-0 basis-0 text-body font-body text-default-font",
            { "text-brand-700": selected }
          )}
        >
          {label}
        </span>
      ) : null}
    </div>
  );
});

interface TreeViewRootProps extends React.HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode;
  className?: string;
}

const TreeViewRoot = React.forwardRef<HTMLElement, TreeViewRootProps>(
  function TreeViewRoot(
    { children, className, ...otherProps }: TreeViewRootProps,
    ref
  ) {
    return children ? (
      <div
        className={SubframeUtils.twClassNames(
          "flex w-full flex-col items-start",
          className
        )}
        ref={ref as any}
        {...otherProps}
      >
        {children}
      </div>
    ) : null;
  }
);

export const TreeView = Object.assign(TreeViewRoot, {
  Folder,
  Item,
});
