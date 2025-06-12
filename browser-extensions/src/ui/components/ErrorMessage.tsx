"use client";
/*
 * Documentation:
 * Error Message — https://app.subframe.com/dd13c78ea6fd/library?component=Error+Message_1ed07ed4-7cd8-4721-b9b4-ef4fcbe09920
 */

import React from "react";
import * as SubframeUtils from "../utils";
import * as SubframeCore from "@subframe/core";
import { FeatherAlertCircle } from "@subframe/core";

interface ErrorMessageRootProps extends React.HTMLAttributes<HTMLDivElement> {
  icon?: React.ReactNode;
  text?: React.ReactNode;
  className?: string;
}

const ErrorMessageRoot = React.forwardRef<HTMLElement, ErrorMessageRootProps>(
  function ErrorMessageRoot(
    {
      icon = <FeatherAlertCircle />,
      text,
      className,
      ...otherProps
    }: ErrorMessageRootProps,
    ref
  ) {
    return (
      <div
        className={SubframeUtils.twClassNames(
          "flex w-full items-center gap-4 rounded-md bg-error-50 px-4 py-4",
          className
        )}
        ref={ref as any}
        {...otherProps}
      >
        {icon ? (
          <SubframeCore.IconWrapper className="text-body font-body text-error-600">
            {icon}
          </SubframeCore.IconWrapper>
        ) : null}
        {text ? (
          <span className="grow shrink-0 basis-0 text-body font-body text-error-600">
            {text}
          </span>
        ) : null}
      </div>
    );
  }
);

export const ErrorMessage = ErrorMessageRoot;
