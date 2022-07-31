package org.quartz.spi;

/**
 * 触发器点火结果
 *
 * @author lorban
 */
public class TriggerFiredResult {

  private TriggerFiredBundle triggerFiredBundle;

  private Exception exception;

  public TriggerFiredResult(TriggerFiredBundle triggerFiredBundle) {
    this.triggerFiredBundle = triggerFiredBundle;
  }

  public TriggerFiredResult(Exception exception) {
    this.exception = exception;
  }

  public TriggerFiredBundle getTriggerFiredBundle() {
    return triggerFiredBundle;
  }

  public Exception getException() {
    return exception;
  }
}
