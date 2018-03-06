package com.ytjojo.lintapp;

import com.ytjojo.module_article.Item;
import com.ytjojo.module_article.Patient;
import io.reactivex.Observable;
import java.util.ArrayList;
import retrofit2.http.ArrayItem;
import retrofit2.http.NgariJsonPost;
import retrofit2.http.POST;

public interface HttpService {

	@POST("*.jsonRequest")
	@NgariJsonPost(serviceId = "eh.rankingInfoService", method = "rankThumbsUp")
	Observable<ArrayList<Item>> getItems(@ArrayItem String id);

	@POST("*.jsonRequest")
	@NgariJsonPost(serviceId = "eh.rankingInfoService", method = "rankThumbsUp")
	Observable<String> rankThumbsUp(@ArrayItem String patientFeedback);

	@POST("*.jsonRequest")
	@NgariJsonPost(serviceId = "eh.rankingInfoService", method = "rankThumbsUp")
	Observable<Patient> getPatient(@ArrayItem String patientFeedback);


}