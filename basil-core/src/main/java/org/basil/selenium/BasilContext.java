/**
 * Copyright (c) 2013-2018 Ryan Li Wan. All rights reserved.
 */

package org.basil.selenium;

import java.util.List;

import org.basil.selenium.base.DriverFactory;
import org.basil.selenium.page.PageObject;
import org.basil.selenium.service.XPathUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * BasilContext - Everything I think it's missing in SearchContext, they are:
 * <ol>
 * <li>Serves as a powerful layer that handles locators, for both its own and ones in find methods
 *   <ul>
 *     <li>It is used as the parent type of BasilElement and PageObject</li>
 *     <li>It take cares whether the locators passed in find methods need to be prefixed</li>
 *   </ul>
 * </li>
 * <li>Besides the locators handling logic, it is also used as a context's parent context
 *   <ul>
 *     <li>Easy to convert to concrete types</li>
 *     <li>Generate XPath</li>
 *   </ul>
 * </li>
 * </ol>
 *
 * @author ryan131
 * @since Sep 29, 2014, 11:21:10 PM
 */
public class BasilContext extends AbstractContext implements SearchContext {

  public static BasilContext create(SearchContext context) {
    return new BasilContext().setContext(context);
  }

  public static BasilContext create(SearchContext context, By locator) {
    return new BasilContext().setContext(context).setLocator(locator);
  }

  private static final Logger logger = LoggerFactory.getLogger(BasilContext.class);

  protected Locator locator;
  protected Context context;
  protected Resolve resolve;

  // Constructor

  protected BasilContext() {
    locator = new Locator();
    context = new Context();
    resolve = new Resolve();
  }

  // Copy

  protected void copy(BasilContext source) {
    locator().copy(source.locator);
    context().copy(source.context);
  }

  // Locator

  @Override
  public Locator locator() {
    return locator;
  }

  @Override
  public BasilContext setLocator(By locator) {
    locator().set(locator);
    return this;
  }

  // Context

  @Override
  public Context context() {
    return context;
  }

  @Override
  protected BasilContext setContext(SearchContext context) {
    context().set(context);
    return this;
  }

  @Override
  protected BasilContext setParent(SearchContext parent) {
    context().setParent(parent);
    return this;
  }

  // Resolve

  @Override
  public Resolve resolve() {
    return resolve;
  }

  /**
   * The locator management mechanism. Not to confused with org.openqa.selenium.By.
   */
  public class Locator implements LocatorManager {

    protected Basil locator;
    protected Basil confident;
    protected Basil generated;

    protected void copy(Locator source) {
      this.locator = source.locator;
      this.confident = source.confident;
      this.generated = source.generated;
    }

    @Override
    public void set(By by) {
      this.locator = Basil.from(Preconditions.checkNotNull(by));
      if (locator.isConfident()) {
        confident = locator;
      }
    }

    @Override
    public boolean has() {
      return locator != null;
    }

    @Override
    public Basil get() {
      if (has()) {
        return locator;
      }
      logger.warn("[" + getClassName() + "] has no locator. Fallback to confident/generated.");
      return locator = getConfident();
    }

    @Override
    public boolean hasConfident() {
      return confident != null;
    }

    @Override
    public Basil getConfident() {
      if (locator != null && locator.isConfident()) {
        confident = locator;
      }
      if (confident == null) {
        confident = getGenerated(false);
      }
      return confident;
    }

    @Override
    public boolean hasGenerated() {
      return generated != null;
    }

    @Override
    public Basil getGenerated() {
      if (isWebDriver()) {
        return generated = Basil.xpath("");
      }
      return getGenerated(resolve.resolutionAvoidance);
    }

    Basil getGenerated(boolean resolutionAvoidance) {
      if (resolutionAvoidance) {
        BasilContext parent = getParent();
        Basil locator = hasGenerated() ? generated : get();
        while (!locator.isConfident()) {
          // Concatenating the locator
          if (parent.locator().hasGenerated()) {
            locator = parent.getGeneratedLocator().concat(locator);
          } else if (parent.locator().hasConfident()) {
            locator = parent.getConfidentLocator().concat(locator);
          } else {
            locator = parent.getLocator().concat(locator);
          }
          parent = parent.getParent();
          if (parent.isWebDriver()) {
            break;
          }
        }
        return generated = locator;
      }
      if (generated == null) {
        String xpathExpression = null;
        if (context().isWebDriver()) {
          xpathExpression = ""; // omitting the "/html"
        }
        if (context().isWebElement()) {
          xpathExpression = XPathUtil.getXPath(context().toWebElement());
        }
        if (context().isPageObject()) {
          if (!context().toPageObject().isInitialized()) {
            context().toPageObject().resolve().resolve();
          }
          xpathExpression = XPathUtil.getXPath(context().toWebElement());
        }
        if (xpathExpression != null) {
          generated = Basil.xpath(xpathExpression);
        } else {
          throw new IllegalArgumentException("Unable to generate locator for " + getClassName());
        }
      }
      return generated;
    }

  }

  /**
   * Maintain the context's resolution status
   * Provide the optimized find element methods
   * Provide assisting methods on the context's type
   */
  public class Context implements ContextManager {

    protected SearchContext context;
    protected BasilContext parent;
    protected WebDriver driver;

    protected Context() {
      this.driver = DriverFactory.getWebDriver();
    }

    protected Context(WebDriver driver) {
      this.driver = driver;
    }

    protected void copy(Context source) {
      this.context = source.context;
      this.parent = source.parent;
      this.driver = source.driver;
    }

    @Override
    public void set(SearchContext context) {
      if (context instanceof BasilContext) {
        BasilContext.this.copy((BasilContext) context);
      } else {
        this.context = Preconditions.checkNotNull(context);
      }
    }

    @Override
    public SearchContext get() {
      return context;
    }

    @Override
    public void setParent(SearchContext parent) {
      if (parent instanceof BasilContext) {
        this.parent = (BasilContext) parent;
      } else {
        this.parent = BasilContext.create(parent);
        if (parent instanceof WebDriver) {
          return;
        }
        logger.warn("A raw parent is used, please use Basil-compatible parent whenever available.");
      }
    }

    @Override
    public BasilContext getParent() {
      if (parent == null) {
        parent = BasilContext.create(DriverFactory.getWebDriver());
      }
      return parent;
    }

    @Override
    public void setDriver(WebDriver driver) {
      this.driver = driver;
    }

    @Override
    public WebDriver getDriver() {
      return driver;
    }

    // Type: WebDriver/WebElement/BasilElement/PageObject

    @Override
    public boolean isWebDriver() {
      return context instanceof WebDriver;
    }

    @Override
    public WebDriver toWebDriver() {
      Preconditions.checkArgument(isWebDriver());
      return (WebDriver) context;
    }

    @Override
    public boolean isWebElement() {
      return context instanceof WebElement;
    }

    @Override
    public WebElement toWebElement() {
      Preconditions.checkArgument(isWebElement());
      return (WebElement) context;
    }

    public boolean isBasilElement() {
      return BasilContext.this instanceof BasilElement;
    }

    public BasilElement toBasilElement() {
      Preconditions.checkArgument(isBasilElement());
      return (BasilElement) BasilContext.this;
    }

    public boolean isPageObject() {
      return BasilContext.this instanceof PageObject;
    }

    public PageObject toPageObject() {
      Preconditions.checkArgument(isPageObject());
      return (PageObject) BasilContext.this;
    }

    // Resolution

    public boolean isResolved() {
      return context != null;
    }

  }

  public class Resolve implements ResolutionManager {

    /**
     * Flag for resolution avoidance
     */
    protected final boolean resolutionAvoidance = true;

    @Override
    public void resolve() {
      Preconditions.checkArgument(context.isBasilElement(), "Resolution is supported for only BasilElements.");
      context.toBasilElement().resolve().resolve();
    }

    @Override
    public boolean isResolved() {
      return context().isResolved();
    }

    @Override
    public List<WebElement> findElements(By by) {
      if (resolutionAvoidance) {
        return resolutionAvoidances(Basil.from(by));
      }
      // No resolve avoidance
      By unconcatenatedBy = by;
      if (Basil.from(by).hasXPath()) {
        by = locator().getConfident().concat(by);
        return driver().findElements(by);
      }
      return context.get().findElements(unconcatenatedBy);
    }

    @Override
    public BasilElement findElement(By by) {
      if (resolutionAvoidance) {
        return resolutionAvoidance(Basil.from(by));
      }
      // XXX Use setParent(BasilContext.this) whenever possible to prevent property loss due to
      // creating the BasilContext from a parent that is raw, i.e. setParent(context().get());.
      By unconcatenatedBy = by;
      if (Basil.from(by).isConfident()) {
        return BasilElement.create(driver().findElement(by)).setParent(driver()).setLocator(by);
      }
      if (Basil.from(by).isByXPath()) {
        by = locator().getConfident().concat(by);
        return BasilElement.create(driver().findElement(by)).setParent(BasilContext.this).setLocator(unconcatenatedBy);
      }
      return BasilElement.create(context.get().findElement(by)).setParent(BasilContext.this).setLocator(by);
    }

    // Resolution avoidance

    List<WebElement> resolutionAvoidances(Basil by) {
      // The same as no resolution avoidance
      By unconcantenatedBy = by;
      if (by.hasXPath()) {
        by = locator().getConfident().concat(by);
        return driver().findElements(by);
      }
      return context.get().findElements(unconcantenatedBy);
    }

    /**
     * Resolution Avoidance strategy
     *
     * 1) If the locator is confident, ignore the parent context and looking it up directly in the
     *    driver. This avoids resolution of the parent, but it should set/accept the parent as the
     *    parent context rather than the driver.
     * 2) If the locator is not confident, 
     */
    BasilElement resolutionAvoidance(Basil by) {
      if (by.isConfident()) {
        return BasilElement.create(driver(), by);
      }
      if (!by.hasXPath()) {
        // Investigate if BasilElement.create(BasilContext.this, by); is better.
        return BasilElement.create(getContext().findElement(by)).setParent(BasilContext.this).setLocator(by);
      }

      Basil unconcatenatedBy = by;
      // Concatenating the locator
      if (context().isResolved() && !locator().hasGenerated()) {
        // Yes, this is a possibility, and yes, this needs to be remedied. Why is this parent
        // that's initialized but has not generated locator? Because it's been out-smarted by
        // resolution avoidance.
        locator().getGenerated(false);
      }
      if (locator().hasGenerated()) {
        by = getGeneratedLocator().concat(by);
      } else if (locator().hasConfident()) {
        by = getConfidentLocator().concat(by);
      }
      // Note: getLocator().concat(by) isn't guaranteed to be confident. For example, When the
      // current context is a table with locator //div[@id='table_1'], when locating the header
      // like //table[@class='tableHeader'] it maybe fine, the getLocator().concat(by) produced
      // //div[@id='table_1']//table[@class='tableHeader'] accurate-ish. But when the context
      // is the header, and we locating a header row, the produced locator is
      // //table[@class='tableHeader']//tr[@class='headerRow'] may result the row from other
      // table to be located.
      if (by.isConfident()) {
        return BasilElement.create(driver().findElement(by)).setParent(BasilContext.this).setLocator(unconcatenatedBy);
      }
      by = unconcatenatedBy;
      return BasilElement.create(getContext().findElement(by)).setParent(BasilContext.this).setLocator(by);
    }

  }

}
