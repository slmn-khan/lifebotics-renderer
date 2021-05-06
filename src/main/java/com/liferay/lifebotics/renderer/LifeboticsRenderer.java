package com.liferay.lifebotics.renderer;

import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderItem;
import com.liferay.commerce.model.CommerceShipment;
import com.liferay.commerce.product.catalog.CPCatalogEntry;
import com.liferay.commerce.product.content.render.CPContentRenderer;
import com.liferay.commerce.product.service.CPDefinitionLocalService;
import com.liferay.commerce.product.type.grouped.util.GroupedCPTypeHelper;
import com.liferay.commerce.product.type.virtual.util.VirtualCPTypeHelper;
import com.liferay.commerce.product.util.CPDefinitionHelper;
import com.liferay.commerce.service.CommerceOrderItemLocalService;
import com.liferay.commerce.service.CommerceOrderItemService;
import com.liferay.commerce.service.CommerceOrderLocalService;
import com.liferay.commerce.service.CommerceShipmentService;
import com.liferay.frontend.taglib.servlet.taglib.util.JSPRenderer;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.WebKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;


@Component(
	    immediate = true,
	    property = {
	        "commerce.product.content.renderer.key="+LifeboticsRenderer.KEY,
	        "commerce.product.content.renderer.type=grouped",
			"commerce.product.content.renderer.type=simple",
			"commerce.product.content.renderer.type=virtual"
	    },
	    service = CPContentRenderer.class
	)
public class LifeboticsRenderer implements CPContentRenderer {
	
	public static final String KEY = "lfrbot";
	
	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public String getLabel(Locale locale) {
		ResourceBundle resourceBundle = ResourceBundleUtil.getBundle(
				"content.Language", locale, getClass());
		return LanguageUtil.get(resourceBundle, KEY);
	}

	@Override
	public void render(CPCatalogEntry cpCatalogEntry, HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse) throws Exception {
		
		boolean is_purchased=false;
		
		long productId = cpCatalogEntry.getCProductId();
		
		ThemeDisplay themeDisplay = (ThemeDisplay)httpServletRequest.getAttribute(WebKeys.THEME_DISPLAY);
		
		int[] orderStatuses= {1,2};
		
		System.out.println("Group ID : "+themeDisplay.getScopeGroupId());
		List<CommerceOrder> commerceOrders =  _commerceOrderLocalService.getCommerceOrders(themeDisplay.getScopeGroupId(), orderStatuses);
		
		commerceOrders = _commerceOrderLocalService.getCommerceOrders(-1, -1);
		
		System.out.println("Number of shipped items :"+commerceOrders.size());
		
		// List<CommerceOrderItem> _commerceOrderItems;
		
		for(CommerceOrder order : commerceOrders) {
			List<CommerceShipment> _commerceShipments=_commerceShipmentService.getCommerceShipmentsByOrderId(order.getCommerceOrderId(), -1, -1);
				for(CommerceShipment _commerceShipment : _commerceShipments) {
					System.out.println("Shipment Status"+_commerceShipment.getStatus());
						if(_commerceShipment.getStatus()==3) {
							List<CommerceOrderItem> commerceOrderItems = order.getCommerceOrderItems();
								for(CommerceOrderItem commerceOrderItem : commerceOrderItems) {
									if(productId == commerceOrderItem.getCProductId())
										is_purchased=true;
								}
						}
				}
		}
		
		if(is_purchased)
			{
				httpServletRequest.setAttribute("bought_by_current_user", "yes");
				httpServletRequest.setAttribute("purchased", "yes");	
				
				
				HttpSession session = httpServletRequest.getSession();
				session.setAttribute("purchased", "yes");
				
				Cookie[] cookies = httpServletRequest.getCookies();

		           if (cookies != null) {
		        	   System.out.println("I am Here ! ");
		            for (Cookie cookie : cookies) {
		              if (cookie.getName().equals("purchased")) {
		                 if(cookie.getValue().equals("no"))
		                 	cookie.setValue("yes");
		               }
		             }
		            Cookie cookie = new Cookie("purchased", "yes");
		            cookie.setMaxAge( 300 );
		            cookie.setPath("/");
		            httpServletResponse.addCookie(cookie);
		           }
		
				
				System.out.println("Whats going on ! ");
			}
		else
			{
			
				httpServletRequest.setAttribute("bought_by_current_user", "no");
				
				Cookie[] cookies = httpServletRequest.getCookies();
				if (cookies != null) {
		            for (Cookie cookie : cookies) {
		              if (cookie.getName().equals("purchased")) {
		                 if(cookie.getValue().equals("yes"))
		                 	{
		                	 cookie.setValue("no");
		                	 System.out.println("This works!");
		                 	}
		                 	
		               }
		             }
		           }
			}
		
		httpServletRequest.setAttribute(
				"groupedCPTypeHelper", _groupedCPTypeHelper);
			httpServletRequest.setAttribute(
				"virtualCPTypeHelper", _virtualCPTypeHelper);
		
		_jspRenderer.renderJSP(
		        _servletContext, httpServletRequest, httpServletResponse,
		        "/render/view.jsp");
		
	}
	
	protected List<CPCatalogEntry> getPurchasedProducts(long companyId, long groupId, 
			long commerceAccountId, Locale locale, int start, int end) 
		throws PortalException {
		
		DynamicQuery productsPurchasedQuery = createPurchasedProductsQuery(companyId, groupId, commerceAccountId);
		
		List<CPCatalogEntry> CPCatalogEntryList = new ArrayList<CPCatalogEntry>();
		List<Long> results = _commerceOrderItemLocalService.dynamicQuery(productsPurchasedQuery,start,end);
		List <Long> cproductIds = new ArrayList<Long>();
		if (results != null) {
			for (Long row : results) {
				cproductIds.add(row);
			}
		}
		if (!cproductIds.isEmpty()) {
			long cpDefinitionId;
			for (Long cproductId : cproductIds) {
				cpDefinitionId = _cpDefinitionLocalService.fetchCPDefinitionByCProductId(cproductId.longValue()).getCPDefinitionId();
				CPCatalogEntryList.add(_cpDefinitionHelper.getCPCatalogEntry(commerceAccountId, groupId, cpDefinitionId, locale));
			}
		}	
		
		return CPCatalogEntryList;
	}
	
	
	private DynamicQuery createPurchasedProductsQuery(long companyId, long groupId, 
			long commerceAccountId) throws PortalException {
		
		// Subquery to get all approved orders for the selected account
		DynamicQuery accountOrders = createAccountOrdersQuery(companyId, groupId, commerceAccountId);
		
		// Query to get all distinct products included in the selected account orders of the subquery
		DynamicQuery purchasedProducts = _commerceOrderItemLocalService.dynamicQuery();
		purchasedProducts
					.add(PropertyFactoryUtil.forName("commerceOrderId").in(accountOrders))
		            .setProjection(ProjectionFactoryUtil.projectionList()
		            .add(ProjectionFactoryUtil.distinct(ProjectionFactoryUtil.property("CProductId"))));

		return purchasedProducts;
	}
	
	
	private DynamicQuery createAccountOrdersQuery (long companyId, long groupId, 
			long commerceAccountId) throws PortalException {
		
		// Query to get all approved orders for the selected account
		DynamicQuery accountOrders = _commerceOrderLocalService.dynamicQuery();
		accountOrders.add(RestrictionsFactoryUtil.eq("commerceAccountId", commerceAccountId))
					 .add(RestrictionsFactoryUtil.eq("groupId", groupId))
					 .add(RestrictionsFactoryUtil.eq("companyId", companyId))
					 .add(RestrictionsFactoryUtil.eq("orderStatus", new Integer(10)))
					 .setProjection(ProjectionFactoryUtil.property("commerceOrderId"));
		
		return accountOrders;
	}
	
	@Reference
	CommerceShipmentService _commerceShipmentService;
	
	@Reference
	CommerceOrderItemLocalService _commerceOrderItemLocalService;
	
	@Reference
	private CommerceOrderItemService _commerceOrderItemService;
	
	@Reference
	private CPDefinitionLocalService _cpDefinitionLocalService;
	
	@Reference
	private CPDefinitionHelper _cpDefinitionHelper;

	@Reference
	private CommerceOrderLocalService _commerceOrderLocalService;
	
	@Reference
	private GroupedCPTypeHelper _groupedCPTypeHelper;

	@Reference
	private JSPRenderer _jspRenderer;

	@Reference(
		target = "(osgi.web.symbolicname=com.liferay.lifebotics.renderer)"
	)
	private ServletContext _servletContext;

	@Reference
	private VirtualCPTypeHelper _virtualCPTypeHelper;

}
