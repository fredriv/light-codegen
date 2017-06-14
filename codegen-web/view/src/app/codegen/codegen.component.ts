import {Component, OnInit} from '@angular/core';
import {Store} from '@ngrx/store';
import {getCodegenStage} from 'app/codegen/codegen.state';
import {AppState} from 'app/app.state';
import {Observable} from 'rxjs/Observable';
import {CodegenStage} from 'app/codegen/codegen.config';
import {CodegenService} from './codegen.service';

import 'rxjs/add/operator/take';

@Component({
	selector: 'app-codegen',
	templateUrl: './codegen.component.html',
	styleUrls: ['./codegen.component.css']
})
export class CodegenComponent implements OnInit {


	codegenStage: Observable<CodegenStage>;
	CODEGEN_STAGE_ENUM = CodegenStage;

	constructor(private store: Store<AppState>, private codegenService: CodegenService) {
		this.codegenStage = this.store.select(getCodegenStage);
	}

	ngOnInit() {}

	onSchemaAdded() {
		this.codegenService.setStage(CodegenStage.CONFIG_SELECTION);
	}

	onBackClick() {
		this.codegenStage.take(1).subscribe((currentStage: CodegenStage) => {
			this.codegenService.setStage(currentStage - 1);
		});
	}

}