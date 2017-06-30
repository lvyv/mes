package com.qcadoo.mes.materialFlowResources.batch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.qcadoo.localization.api.TranslationService;
import com.qcadoo.mes.basic.BasicLookupController;
import com.qcadoo.mes.basic.GridResponse;
import com.qcadoo.mes.basic.LookupUtils;
import com.qcadoo.mes.materialFlowResources.DocumentPositionService;
import com.qcadoo.mes.materialFlowResources.ResourceDTO;
import com.qcadoo.mes.materialFlowResources.WarehouseMethodOfDisposalService;

@Controller
@RequestMapping(value = "resource")
public class ResourceLookupController extends BasicLookupController<ResourceDTO> {

    @Autowired
    private DocumentPositionService documentPositionService;

    @Autowired
    private WarehouseMethodOfDisposalService warehouseMethodOfDisposalService;

    @Autowired
    private LookupUtils lookupUtils;

    @Autowired
    private TranslationService translationService;

    @Override
    @ResponseBody
    @RequestMapping(value = "records", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public GridResponse<ResourceDTO> getRecords(@RequestParam String sidx, @RequestParam String sord,
            @RequestParam(defaultValue = "1", required = false, value = "page") Integer page,
            @RequestParam(value = "rows") int perPage,
            @RequestParam(defaultValue = "0", required = false, value = "context") Long context, ResourceDTO record) {

        return getResponse(sidx, sord, page, perPage, record, context);

    }

    private GridResponse<ResourceDTO> getResponse(String sidx, String sord, Integer page, int perPage, ResourceDTO record,
            Long context) {
        String additionalCode = record.getAc();
        boolean useAdditionalCode = org.apache.commons.lang3.StringUtils.isNotEmpty(additionalCode);
        Map<String, Object> parameters = geParameters(context, record, useAdditionalCode, additionalCode);

        boolean properFilter = prepareWasteFilter(record);
        if ("wasteString".equals(sidx)) {
            sidx = "waste";
        }
        boolean properFilterLastResource = prepareLastResourceFilter(record);
        if ("lastResourceString".equals(sidx)) {
            sidx = "lastResource";
        }
        String query = getQuery(context, useAdditionalCode, !properFilter, !properFilterLastResource);

        GridResponse<ResourceDTO> response = lookupUtils.getGridResponse(query, sidx, sord, page, perPage, record, parameters);

        if (response.getRows().isEmpty() && useAdditionalCode) {
            parameters = geParameters(context, record, false, additionalCode);
            query = getQuery(context, false, !properFilter, !properFilterLastResource);
            response = lookupUtils.getGridResponse(query, sidx, sord, page, perPage, record, parameters);
        }
        setTranslatedWasteFlag(response);
        setTranslatedLastResourceFlag(response);
        return response;
    }

    protected String getQuery(final Long context, boolean useAdditionalCode, boolean wasteFilterIsWrong,
            boolean lastResourceFilterIsWrong) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder
                .append("select %s from (select r.*, sl.number as storageLocation, pn.number as palletNumber, ac.code as additionalCode, bp.unit as unit, ");
        queryBuilder.append("r1.resourcesCount < 2 AS lastResource ");
        queryBuilder.append("FROM materialflowresources_resource r ");
        queryBuilder
                .append("LEFT JOIN (SELECT palletnumber_id, count(id) as resourcesCount FROM materialflowresources_resource GROUP BY palletnumber_id) r1 ON r1.palletnumber_id = r.palletnumber_id \n");
        queryBuilder.append("LEFT JOIN materialflowresources_storagelocation sl on sl.id = storageLocation_id ");
        queryBuilder.append("LEFT JOIN basic_additionalcode ac on ac.id = additionalcode_id ");
        queryBuilder.append("LEFT JOIN basic_product bp on bp.number = :product ");
        queryBuilder.append("LEFT JOIN basic_palletnumber pn on pn.id = r.palletnumber_id WHERE r.product_id = bp.id ");
        queryBuilder
                .append(" AND r.location_id in (SELECT DISTINCT COALESCE(locationfrom_id, locationto_id) as location from materialflowresources_document WHERE id = :context)");
        queryBuilder.append(" AND r.conversion = :conversion AND r.availablequantity > 0 ");
        if (wasteFilterIsWrong) {
            queryBuilder.append(" AND waste IS NULL ");
        }
        if (lastResourceFilterIsWrong) {
            queryBuilder.append(" AND lastresource IS NULL ");
        }
        if (useAdditionalCode) {
            // queryBuilder.append(" AND additionalcode_id = (SELECT id FROM basic_additionalcode WHERE code = :add_code) ");
        }
        queryBuilder.append(warehouseMethodOfDisposalService.getSqlOrderByForResource(context));
        queryBuilder.append(") as resources");
        return queryBuilder.toString();
    }

    protected Map<String, Object> geParameters(Long context, ResourceDTO resourceDTO, boolean useAdditionalCode,
            String additionalCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("product", resourceDTO.getProduct());
        params.put("conversion", resourceDTO.getConversion());
        params.put("context", context);

        if (useAdditionalCode) {
            params.put("add_code", additionalCode);
        }

        resourceDTO.setProduct(null);
        resourceDTO.setConversion(null);
        resourceDTO.setAc(null);

        return params;
    }

    @Override
    protected List<String> getGridFields() {
        return Arrays.asList(new String[] { "number", "quantity", "unit", "quantityInAdditionalUnit", "givenUnit",
                "reservedQuantity", "availableQuantity", "expirationDate", "storageLocation", "batch", "palletNumber",
                "additionalCode", "wasteString", "lastResourceString" });
    }

    @Override
    protected String getRecordName() {
        return "resource";
    }

    @Override
    protected String getQueryForRecords(Long context) {
        return null;
    }

    private void setTranslatedWasteFlag(GridResponse<ResourceDTO> responce) {
        String yes = translationService.translate("documentGrid.gridColumn.wasteString.value.yes",
                LocaleContextHolder.getLocale());
        String no = translationService.translate("documentGrid.gridColumn.wasteString.value.no", LocaleContextHolder.getLocale());
        responce.getRows().forEach(resDTO -> resDTO.setWasteString(resDTO.isWaste() ? yes : no));
    }

    private void setTranslatedLastResourceFlag(GridResponse<ResourceDTO> response) {
        String yes = translationService.translate("documentGrid.gridColumn.wasteString.value.yes",
                LocaleContextHolder.getLocale());
        String no = translationService.translate("documentGrid.gridColumn.wasteString.value.no", LocaleContextHolder.getLocale());
        response.getRows().forEach(resDTO -> resDTO.setLastResourceString(resDTO.getLastResource() ? yes : no));
    }

    private boolean prepareWasteFilter(ResourceDTO record) {
        String yes = translationService.translate("documentGrid.gridColumn.wasteString.value.yes",
                LocaleContextHolder.getLocale()).toLowerCase();
        String no = translationService.translate("documentGrid.gridColumn.wasteString.value.no", LocaleContextHolder.getLocale())
                .toLowerCase();
        String filter = record.getWasteString();
        record.setWasteString(null);
        if (filter != null) {
            filter = filter.toLowerCase();
        }
        if (yes.equals(filter)) {
            record.setWaste(true);
        } else if (no.equals(filter)) {
            record.setWaste(false);
        } else if (!StringUtils.isEmpty(filter)) {
            return false;
        } else {
            record.setWaste(null);
        }
        return true;
    }

    private boolean prepareLastResourceFilter(ResourceDTO record) {
        String yes = translationService.translate("documentGrid.gridColumn.wasteString.value.yes",
                LocaleContextHolder.getLocale()).toLowerCase();
        String no = translationService.translate("documentGrid.gridColumn.wasteString.value.no", LocaleContextHolder.getLocale())
                .toLowerCase();
        String filter = record.getLastResourceString();
        record.setLastResourceString(null);
        if (filter != null) {
            filter = filter.toLowerCase();
        }
        if (yes.equals(filter)) {
            record.setLastResource(true);
        } else if (no.equals(filter)) {
            record.setLastResource(false);
        } else if (!StringUtils.isEmpty(filter)) {
            return false;
        } else {
            record.setLastResource(null);
        }
        return true;
    }

}
